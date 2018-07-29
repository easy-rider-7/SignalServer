package org.whispersystems.textsecuregcm.controllers;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import org.apache.commons.codec.binary.Base64;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.valuehandling.UnwrapValidatedValue;
import org.whispersystems.textsecuregcm.configuration.ProfilesConfiguration;
import org.whispersystems.textsecuregcm.entities.Profile;
import org.whispersystems.textsecuregcm.entities.ProfileAvatarUploadAttributes;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.util.Pair;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.net.URL;
import io.minio.MinioClient;

import io.dropwizard.auth.Auth;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidArgumentException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.NoResponseException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.whispersystems.textsecuregcm.s3.UrlSigner;
import org.xmlpull.v1.XmlPullParserException;

@Path("/v1/profile")
public class ProfileController {

  private final RateLimiters     rateLimiters;
  private final AccountsManager  accountsManager;

  private final PolicySigner        policySigner;
  private final PostPolicyGenerator policyGenerator;

  //private final AmazonS3            s3client;
  private MinioClient         minioClient;
  //private final UrlSigner     urlSigner;
  private final String        bucket;

  private final String              accessKey;
  private final String              accessSecret;

  public ProfileController(RateLimiters rateLimiters,
                           AccountsManager accountsManager,
                           ProfilesConfiguration configuration)
  {
    //AWSCredentials         credentials         = new BasicAWSCredentials(profilesConfiguration.getAccessKey(), profilesConfiguration.getAccessSecret());
    //AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);

    this.rateLimiters       = rateLimiters;
    this.accountsManager    = accountsManager;
    this.bucket             = configuration.getBucket();
    this.accessKey          = configuration.getAccessKey();
    this.accessSecret       = configuration.getAccessSecret();

    this.policyGenerator  = new PostPolicyGenerator(configuration.getRegion(),
                                                    configuration.getBucket(),
                                                    configuration.getAccessKey());
          
    this.policySigner     = new PolicySigner(configuration.getAccessSecret(),
                                             configuration.getRegion());
    //this.urlSigner = new UrlSigner(configuration);

    /*this.s3client           = AmazonS3Client.builder()
    .withCredentials(credentialsProvider)
    .withRegion(profilesConfiguration.getRegion())
    .build();*/
      try {
          
          URL serverURL = new URL(configuration.getServer());
                    
          this.minioClient      = new MinioClient(serverURL, this.accessKey, this.accessSecret);
          
      } catch (MalformedURLException ex) {
          Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidEndpointException ex) {
          Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidPortException ex) {
          Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
      }
  }
  
  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{number}")
  public Profile getProfile(@Auth Account account,
                            @PathParam("number") String number,
                            @QueryParam("ca") boolean useCaCertificate)
      throws RateLimitExceededException
  {
    rateLimiters.getProfileLimiter().validate(account.getNumber());

    Optional<Account> accountProfile = accountsManager.get(number);

    if (!accountProfile.isPresent()) {
      throw new WebApplicationException(Response.status(404).build());
    }
    
    Account numberAccount = accountProfile.get();
        
    return new Profile(numberAccount.getName(),
                       numberAccount.getAvatar(),
                       numberAccount.getIdentityKey());
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/name/{name}")
  public void setProfile(@Auth Account account, @PathParam("name") @UnwrapValidatedValue(true) @Length(min = 72,max= 72) Optional<String> name) {
    account.setName(name.orNull());
    accountsManager.update(account);
  }


  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/form/avatar")
  public ProfileAvatarUploadAttributes getAvatarUploadForm(@Auth Account account) {
    String               previousAvatar = account.getAvatar();
    ZonedDateTime        now            = ZonedDateTime.now(ZoneOffset.UTC);
    String               objectName     = generateAvatarObjectName();
    Pair<String, String> policy         = policyGenerator.createFor(now, objectName);
    String               signature      = policySigner.getSignature(now, policy.second());

    if (previousAvatar != null && previousAvatar.startsWith("profiles/")) {
        try {
            minioClient.removeObject(bucket, previousAvatar);
        } catch (InvalidBucketNameException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InsufficientDataException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoResponseException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XmlPullParserException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ErrorResponseException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InternalException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidArgumentException ex) {
            Logger.getLogger(ProfileController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    account.setAvatar(objectName);
    accountsManager.update(account);

    return new ProfileAvatarUploadAttributes(objectName, policy.first(), "private", "AWS4-HMAC-SHA256",
                                             now.format(PostPolicyGenerator.AWS_DATE_TIME), policy.second(), signature);
  }

  private String generateAvatarObjectName() {
    byte[] object = new byte[16];
    new SecureRandom().nextBytes(object);

    return "profiles/" + Base64.encodeBase64URLSafeString(object);
  }
}
