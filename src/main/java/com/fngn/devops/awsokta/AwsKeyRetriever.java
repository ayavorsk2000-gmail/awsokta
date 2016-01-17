package com.fngn.devops.awsokta;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This code was mostly extracted from a sample java code provided by OKTA team. AwsKeyRetriever fetches temporary
 * AWS keys to be used with Amazon CLI. User must have Aws access already and available as a "chiclet" on the main
 * OKTA page.
 * 
 * @author ayavorskiy
 *
 */
public class AwsKeyRetriever {

  private String _strOktaOrgDomain;
  private String _strOktaUserName;
  private String _strOktaUserPAssword;
  private String _strAwsAppUrl;
  private String _strRoleName;
  private boolean _bSilence;

  /**
   * Utility structure to hold ARN information found in SAML request.
   */
  private static final class RoleArnHolder {
    String principalArn;
    String roleArn;
  }

  public void setSilence(boolean bSilence) {
    _bSilence = bSilence;
  }

//  public String getOktaOrgDomainName() {
//    return _strOktaOrgDomain;
//  }

  /**
   * Sets custom OKTA domain name for the organization. Example: fngn.okta.com
   * 
   * @param strValue
   */
  public void setOktaOrgDomainName(String strValue) {
    _strOktaOrgDomain = strValue;
  }

  public String getOktaUserName() {
    return _strOktaUserName;
  }

  public void setOktaUserName(String strValue) {
    _strOktaUserName = strValue;
  }

//  public String getOktaUserPassword() {
//    return _strOktaUserPAssword;
//  }

  public void setOktaUserPassword(String strVal) {
    _strOktaUserPAssword = strVal;
  }

  /**
   * Sets AwsApp URL as found on the OKTA home page. Right-click on the AWS chiclet to see the URL.
   * 
   * Example: https://fngn.okta.com/home/amazon_aws/0oa15buyl22hF0uUA1d8/272
   * 
   * @return
   */
  public String getAwsAppUrl() {
    return _strAwsAppUrl;
  }

  public void setAwsAppUrl(String strValue) {
    _strAwsAppUrl = strValue;
  }

  /**
   * Authenticates with OKTA based on supplied information and assumes first available role for the currently
   * logged in user.
   * 
   * @return set of AWS keys to be used for CLI access
   */
  public AwsKeyData retrieveKey() {

    String strSAML = null;
    List<RoleArnHolder> lstRoleArns = null;
    AwsKeyData keyData = null;

    strSAML = _authenticateAndGetSaml();

    lstRoleArns = _parseRoleArn(strSAML);

    System.out.println("Found " + lstRoleArns.size() + " role(s):");

    if ( lstRoleArns.size() == 0 ) {
      System.err.println("ERROR: You have no AWS roles to assume on this environment.");
      return null;
    }
    
    // for now just pick the first one
    RoleArnHolder roleToAssume = lstRoleArns.get(0);

    for (RoleArnHolder roleArnHolder : lstRoleArns) {
      System.out.print("\t");
      System.out.print(roleArnHolder.principalArn);
      System.out.print(",");
      System.out.println(roleArnHolder.roleArn);

      // if role is specified, try to find a match.
      // role name shows up at the very end of anr block
      if (_strRoleName != null && roleArnHolder.roleArn.endsWith(_strRoleName)) {
        roleToAssume = roleArnHolder;
      }
    }

    System.out.println("Assuming role: ");
    System.out.print("\t");
    System.out.print(roleToAssume.principalArn);
    System.out.print(",");
    System.out.println(roleToAssume.roleArn);

    //
    keyData = _assumeRoleAndGetKeyData(strSAML, roleToAssume);

    return keyData;
  }

  /**
   * Part 1: Connect to Okta and authenticate the user. This will return a token that can be used to generate and
   * capture the SAML for AWS
   * 
   * @return SAML result
   */
  private String _authenticateAndGetSaml() {

    CloseableHttpClient httpClient = null;
    CloseableHttpResponse responseAuthenticate = null;
    CloseableHttpResponse responseSAML = null;
    HttpGet httpget = null;
    String strResultSAML = "";

    HttpPost httpPost = null;
    httpPost = new HttpPost("https://" + _strOktaOrgDomain + "/api/v1/authn");
    httpPost.addHeader("Accept", "application/json");
    httpPost.addHeader("Content-Type", "application/json");
    httpPost.addHeader("Cache-Control", "no-cache");

    JSONObject jsonObjRequest = new JSONObject();
    jsonObjRequest.put("username", _strOktaUserName);
    jsonObjRequest.put("password", _strOktaUserPAssword);

    try {

      StringEntity entity = new StringEntity(jsonObjRequest.toString());

      entity.setContentType("application/json");
      httpPost.setEntity(entity);

      // send POST request
      httpClient = HttpClients.createDefault();

      responseAuthenticate = httpClient.execute(httpPost);

      if (responseAuthenticate.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException(
            "Failed : HTTP error code : " + responseAuthenticate.getStatusLine().getStatusCode());
      }

      BufferedReader br =
          new BufferedReader(new InputStreamReader((responseAuthenticate.getEntity().getContent())));

      String outputAuthenticate = br.readLine();

      if (_bSilence == false) {
        System.out.println(outputAuthenticate);
      }

      JSONObject jsonObjResponse = new JSONObject(outputAuthenticate);

      String sessionToken = jsonObjResponse.getString("sessionToken");

      if (_bSilence == false) {
          System.out.println("User authenticated with token:" + sessionToken);
      }

      httpget = new HttpGet(_strAwsAppUrl + "?onetimetoken=" + sessionToken);

      responseSAML = httpClient.execute(httpget);

      if (responseSAML.getStatusLine().getStatusCode() != 200) {

        throw new RuntimeException("Failed : HTTP error code : " + responseSAML.getStatusLine().getStatusCode());
      }

      BufferedReader brSAML = new BufferedReader(new InputStreamReader((responseSAML.getEntity().getContent())));

      System.out.println("Reading SAML response from Okta...");

      String outputSAML = "";

      while ((outputSAML = brSAML.readLine()) != null) {

        // System.out.println(outputSAML);

        if (outputSAML.contains("SAMLResponse")) {
          strResultSAML = outputSAML.substring(outputSAML.indexOf("value=") + 7, outputSAML.indexOf("/>") - 1);
        }
      }

      strResultSAML = strResultSAML.replace("&#x2b;", "+").replace("&#x3d;", "=");

    } catch (MalformedURLException mfe) {

      mfe.printStackTrace();

    } catch (IOException ioe) {

      ioe.printStackTrace();

    } finally {

      try {

        responseAuthenticate.close();

        if (responseSAML != null) {
          responseSAML.close();
        }

        httpClient.close();

      } catch (Exception ex) {
        ex.printStackTrace();
      }

    }

    return strResultSAML;
  }

  /**
   * Find role to assume in the supplied SAMLResponse and parse it into a holder structure.
   * 
   * @param resultSAML
   * @return
   */
  private List<RoleArnHolder> _parseRoleArn(String resultSAML) {

    List<RoleArnHolder> lstResult = new ArrayList<RoleArnHolder>();
    String resultSAMLDecoded = new String(Base64.decodeBase64(resultSAML));

    System.out.println("Decoding SAML response from Okta...");

    int nArnStartIndex = resultSAMLDecoded.indexOf("arn:aws");
    int nArnEndIndex = 0;

    while (nArnStartIndex >= 0) {

      nArnEndIndex = resultSAMLDecoded.indexOf("</saml2:AttributeValue");

      String resultSAMLRole = resultSAMLDecoded.substring(nArnStartIndex, nArnEndIndex);

      String[] parts = resultSAMLRole.split(",");

      RoleArnHolder arnHolder = new RoleArnHolder();

      arnHolder.principalArn = parts[0];;
      arnHolder.roleArn = parts[1];

      lstResult.add(arnHolder);

      // trim SAML string and look for more roles
      resultSAMLDecoded = resultSAMLDecoded.substring(nArnEndIndex + 22);

      nArnStartIndex = resultSAMLDecoded.indexOf("arn:aws");
    }

    return lstResult;
  }

  /**
   * Part 2: Extract the SAML, principalArn and roleArn from the Okta-generated SAML
   * 
   * @param resultSAML
   * @return
   */
  private AwsKeyData _assumeRoleAndGetKeyData(String resultSAML, RoleArnHolder roleArnHolder) {

    AwsKeyData result = new AwsKeyData();

    try {

      // Part 3: Call AWS Security Token Service (STS) AssumeRolewithSAML
      // API, passing the principalArn, RoleArn and SAML

      AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient();
      
      AssumeRoleWithSAMLRequest request =
          new AssumeRoleWithSAMLRequest().withPrincipalArn(roleArnHolder.principalArn)
              .withRoleArn(roleArnHolder.roleArn).withSAMLAssertion(resultSAML);

      AssumeRoleWithSAMLResult assumeResult = stsClient.assumeRoleWithSAML(request);

      // Note: This will overwrite your current AWS credentials.

      BasicSessionCredentials temporaryCredentials =
          new BasicSessionCredentials(assumeResult.getCredentials().getAccessKeyId(),
              assumeResult.getCredentials().getSecretAccessKey(), assumeResult.getCredentials().getSessionToken());

      result.setKeyId(temporaryCredentials.getAWSAccessKeyId());
      result.setSecretKey(temporaryCredentials.getAWSSecretKey());
      result.setSessionToken(temporaryCredentials.getSessionToken());

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  /**
   * Set optional role to assume in case multiple roles are available.
   * 
   * @param strVal
   */
  public void setRoleName(String strVal) {
    _strRoleName = strVal;
  }

}
