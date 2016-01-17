package com.fe.devops.awsokta;

import static org.junit.Assert.*;

import org.junit.Test;

import com.fngn.devops.awsokta.AwsKeyData;
import com.fngn.devops.awsokta.AwsKeyRetriever;

public class AwsKeyRetrieverTest {

  @Test
  public void testRetrieveKey() {
    
    // disable test for now since we can't provide real password
    if ( true ) {
      return;
    }
    
    AwsKeyRetriever retriever = new AwsKeyRetriever();

    retriever.setSilence(false);

    retriever.setOktaOrgDomainName("fngn.okta.com");
    retriever.setOktaUserName("ayavorskiy");
    retriever.setOktaUserPassword("<TO DO>");
    
    
    retriever.setAwsAppUrl("https://fngn.okta.com/home/amazon_aws/0oa15buyl22hF0uUA1d8/272");

    AwsKeyData result = retriever.retrieveKey();

    assertNotNull(result.getKeyId());
    assertNotNull(result.getSecretKey());
    assertNotNull(result.getSessionToken());

    System.out.println();
    System.out.println("Succes!");

    System.out.println("ID: " + result.getKeyId());
    System.out.println("SECRET: " + result.getSecretKey());
    System.out.println("SESSION: " + result.getSessionToken());

  }

}
