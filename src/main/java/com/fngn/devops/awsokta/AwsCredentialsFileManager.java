package com.fngn.devops.awsokta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class AwsCredentialsFileManager {

  private static final String AWS_CREDENTIALS_FILE = "credentials";
  private static final String AWS_CONFIG_FILE = "config";
  private AwsKeyData _data;
  private String _strCredentialsFileName;
  private String _strFileName;
  
  /**
   * Overrides default credentails file name. By default, it will use 
   * \Users\<username>\.aws\credentials
   * @param strFileName
   */
  public void setCredentialsFileName( String strFileName ) {
    _strFileName = strFileName;
  }


  /**
   * Saves AWS credentials file as a default profile in user's home directory
   * 
   * e.g. [user home]/.aws/credentials
   * 
   * @return credentials file
   * @throws FileNotFoundException
   * @throws UnsupportedEncodingException
   */
  public File save(AwsKeyData data) throws FileNotFoundException, UnsupportedEncodingException {

    String strFileName = _strFileName == null ? 
          ( getCredentialsDir() + AWS_CREDENTIALS_FILE ) : _strFileName;
    
    File file = new File( strFileName ) ;

    // ensure directory structure is setup
    if ( file.getParentFile() != null ) {
      file.getParentFile().mkdirs();
    }

    // write out the file
    PrintWriter writer = new PrintWriter(file, "UTF-8");

    writer.println("[default]");
    writer.println("aws_access_key_id=" + data.getKeyId());
    writer.println("aws_secret_access_key=" + data.getSecretKey());
    writer.println("aws_session_token=" + data.getSessionToken());
    writer.println("aws_security_token=" + data.getSessionToken());

    writer.close();

    return file;
  }


  /**
   * Deletes existing credential files and prepares default empty file required for the program to work.
   * It was discovered that AWS SDK does not work if there is no credentials file already, even an empty one.
   * @throws UnsupportedEncodingException 
   * @throws FileNotFoundException 
   */
  public void initializeCredentialsFileDirectory() throws FileNotFoundException, UnsupportedEncodingException {
    
    File fileCredentials = new File(getCredentialsDir() + AWS_CREDENTIALS_FILE);
    File fileConfig = new File(getCredentialsDir() + AWS_CONFIG_FILE);
    
    // delete config file
    if (fileConfig.exists() ) {
      fileConfig.delete();
    }
    
    // delete credentials
    if ( fileCredentials.exists() ) {
      fileCredentials.delete();
    }
    
    // write our default file 
    // ensure directory structure is setup
    fileCredentials.getParentFile().mkdirs();

    // write out the default empty file
    PrintWriter writer = new PrintWriter(fileCredentials, "UTF-8");

    writer.println("[default]");
    writer.println("aws_access_key_id=" );
    writer.println("aws_secret_access_key=" );

    writer.close();
    
  }
  
  public String getCredentialsDir() {
    return System.getProperty("user.home") + "/.aws/";
  }
  
}
