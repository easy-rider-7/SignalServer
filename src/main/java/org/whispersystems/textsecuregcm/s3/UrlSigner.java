/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.s3;

import org.whispersystems.textsecuregcm.configuration.AttachmentsConfiguration;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidExpiresRangeException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.NoResponseException;
import java.io.IOException;
import java.net.MalformedURLException;

import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xmlpull.v1.XmlPullParserException;

public class UrlSigner {

  private static final Integer   DURATION_IN_SECONDS = 60 * 60;

  private final String accessKey;
  private final String accessSecret;
  private final String bucket;
  private URL serverURL; 

  public UrlSigner(AttachmentsConfiguration config) {
    //this.credentials = new BasicAWSCredentials(config.getAccessKey(), config.getAccessSecret());
    this.bucket = config.getBucket();
    this.accessKey = config.getAccessKey();
    this.accessSecret = config.getAccessSecret();
    try {
          this.serverURL = new URL(config.getServer());
     } catch (MalformedURLException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
          this.serverURL = null;
     }
  }

  public URL getPreSignedUrl(String objectName, boolean isGet, boolean unaccelerated)  {
      try {
          //    AmazonS3                    client  = new AmazonS3Client(credentials);
          //GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, String.valueOf(attachmentId), method);
          
          //request.setExpiration(new Date(System.currentTimeMillis() + DURATION));
          //request.setContentType("application/octet-stream");
          
          /*if (unaccelerated) {
          client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
          } else {
          client.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build());
          }*/
          MinioClient client = new MinioClient(this.serverURL, this.accessKey, this.accessSecret);
          String urlString = (isGet) ? 
                                client.presignedGetObject(bucket, objectName, DURATION_IN_SECONDS) :
                                client.presignedPutObject(bucket, objectName, DURATION_IN_SECONDS);
        
        return new URL(urlString);//client.generatePresignedUrl(request

      } catch (InvalidEndpointException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidPortException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (MalformedURLException ex) {
        Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidBucketNameException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (NoSuchAlgorithmException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InsufficientDataException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidKeyException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (NoResponseException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (XmlPullParserException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (ErrorResponseException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InternalException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      } catch (InvalidExpiresRangeException ex) {
          Logger.getLogger(UrlSigner.class.getName()).log(Level.SEVERE, null, ex);
      }
    return null;
  }
}
    

