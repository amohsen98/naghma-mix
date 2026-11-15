package com.naghma.catalog.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3-compatible storage (RustFS in development). Browsers upload straight to storage with
 * short-lived presigned URLs, so audio never passes through the service.
 */
@ApplicationScoped
public class StorageService {

    @ConfigProperty(name = "naghma.s3.endpoint")
    String endpoint;

    @ConfigProperty(name = "naghma.s3.public-endpoint")
    String publicEndpoint;

    @ConfigProperty(name = "naghma.s3.region", defaultValue = "us-east-1")
    String region;

    @ConfigProperty(name = "naghma.s3.access-key")
    String accessKey;

    @ConfigProperty(name = "naghma.s3.secret-key")
    String secretKey;

    @ConfigProperty(name = "naghma.s3.bucket")
    String bucket;

    @ConfigProperty(name = "naghma.s3.upload-url-ttl", defaultValue = "PT15M")
    Duration uploadTtl;

    private S3Client client;
    private S3Presigner presigner;

    void onStart(@Observes StartupEvent ev) {
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(creds)
                .serviceConfiguration(pathStyle)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        // Signed for the host the browser will call, not the in-cluster hostname.
        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .region(Region.of(region))
                .credentialsProvider(creds)
                .serviceConfiguration(pathStyle)
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            client.createBucket(b -> b.bucket(bucket));
            Log.infof("created bucket %s", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(b -> b.bucket(bucket));
                Log.infof("created bucket %s", bucket);
            } else {
                Log.warnf("could not check bucket %s: %s", bucket, e.getMessage());
            }
        } catch (RuntimeException e) {
            // storage may still be starting; uploads will fail clearly until it is reachable
            Log.warnf("storage not reachable at startup: %s", e.getMessage());
        }
    }

    public String bucket() {
        return bucket;
    }

    public String presignPut(String key, String contentType, long sizeBytes) {
        return presigner.presignPutObject(p -> p
                        .signatureDuration(uploadTtl)
                        .putObjectRequest(r -> r.bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes)))
                .url()
                .toString();
    }

    /** Size of the stored object, or -1 if it is missing. */
    public long size(String key) {
        try {
            HeadObjectResponse head = client.headObject(h -> h.bucket(bucket).key(key));
            return head.contentLength();
        } catch (NoSuchKeyException e) {
            return -1;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return -1;
            }
            throw e;
        }
    }

    public void deleteQuietly(String key) {
        try {
            client.deleteObject(d -> d.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            Log.warnf("could not delete %s: %s", key, e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        if (presigner != null) {
            presigner.close();
        }
        if (client != null) {
            client.close();
        }
    }
}
