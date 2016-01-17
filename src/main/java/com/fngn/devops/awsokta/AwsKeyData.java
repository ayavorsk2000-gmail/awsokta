package com.fngn.devops.awsokta;

public class AwsKeyData {

  private String _strAccesskeyId;
  private String _strSecretAccessKey;
  private String _strSessionToken;

  public String getKeyId() {
    return _strAccesskeyId;
  }

  public void setKeyId(String strAccesskeyId) {
    _strAccesskeyId = strAccesskeyId;
  }

  public String getSecretKey() {
    return _strSecretAccessKey;
  }

  public void setSecretKey(String strSecretAccessKey) {
    _strSecretAccessKey = strSecretAccessKey;
  }

  public String getSessionToken() {
    return _strSessionToken;
  }

  public void setSessionToken(String strSessionToken) {
    _strSessionToken = strSessionToken;
  }

}
