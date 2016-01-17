package com.fngn.devops.awsokta;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Retrieves temporary AWS keys through OKTA and stores them in credentials file in user directory.
 * 
 * @author ayavorskiy
 *
 */
public class AwsKeyRetrieverMain {

  // CLI Options
  private static final String OPT_HELP = "help";
  private static final String OPT_OKTA_AWS_ENV = "oktaAwsEnv";
  private static final String OPT_OKTA_AWS_URL = "oktaAwsUrl";
  private static final String OPT_OKTA_ORG_DOMAIN = "oktaOrgDomain";
  private static final String OPT_PASSWORD = "password";
  private static final String OPT_USER = "user";
  private static final String OPT_ROLE_NAME = "awsRole";
  private static final String OPT_SILENCE = "silence";
  private static final String OPT_CONFIG = "config";
  private static final String OPT_FILE = "file";
  
  
  public static void main(String[] args) throws Exception {

    Options options = _initOptions();


    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);

      if (cmd.hasOption(OPT_HELP)) {
        _printHelp(options);
        System.exit(0);
      }
      
      

    } catch (ParseException p) {
      _printHelp(options);
      System.exit(-1);
    }

    if ( !cmd.hasOption(OPT_OKTA_AWS_ENV) && !cmd.hasOption(OPT_OKTA_AWS_URL)) {
      
      System.err.println("ERROR: Either " +  OPT_OKTA_AWS_ENV + " or " + OPT_OKTA_AWS_URL +  " must be specified. See -h for more details.");
      
      System.exit(0);
    }
    
    
    
    AwsCredentialsFileManager credFileManager = new AwsCredentialsFileManager();
    
    Properties props = _loadProperties(cmd, credFileManager.getCredentialsDir() );


    AwsKeyRetriever keyRetriever = new AwsKeyRetriever();

    // Set silence mode
    keyRetriever.setSilence(cmd.hasOption(OPT_SILENCE));

    // set user name and password
    keyRetriever.setOktaUserName(cmd.getOptionValue(OPT_USER, System.getProperty("user.name")));
    keyRetriever.setOktaOrgDomainName(cmd.getOptionValue(OPT_OKTA_ORG_DOMAIN, props.getProperty(OPT_OKTA_ORG_DOMAIN)));

    // default AWS environment if one is not supplied
    String strEnv = null;
    
    if (cmd.hasOption(OPT_OKTA_AWS_ENV)) {
      strEnv = cmd.getOptionValue(OPT_OKTA_AWS_ENV);
      String strUrl = props.getProperty(strEnv);
      
      if ( strUrl == null ) {
        System.err.println("ERROR: Unknown environment '" + strEnv + "'. Please, see documentation for details on configuring new environments.");
        System.exit(1);
      }
      
      keyRetriever.setAwsAppUrl( strUrl );
    }

    System.out
        .println("NOTE: You existing credentials file will be deleted. Stop and back it up first, if you care.");

    // prompt for password if not supplied
    if (cmd.hasOption(OPT_PASSWORD)) {
      keyRetriever.setOktaUserPassword(cmd.getOptionValue(OPT_PASSWORD));
    } else {
      Console c = System.console();
      keyRetriever.setOktaUserPassword(new String(c.readPassword("Password:")));
    }

    // Apply role if one is specified
    keyRetriever.setRoleName(cmd.getOptionValue(OPT_ROLE_NAME, null));

    System.out.println("USER:" + keyRetriever.getOktaUserName());
    System.out.println("ENVIRONMENT: (" + strEnv + ") " + keyRetriever.getAwsAppUrl());

    // cleanup before attempting to retrieve a new key
    credFileManager.initializeCredentialsFileDirectory();
    
    AwsKeyData keyData = keyRetriever.retrieveKey();

    if ( cmd.hasOption(OPT_FILE)) {
      credFileManager.setCredentialsFileName( cmd.getOptionValue(OPT_FILE));
    }
    
    if ( keyData != null ) {
      // Save key in the credential file
      File file = credFileManager.save(keyData);

      System.out.println();
      System.out.println("Saved secret key in " + file.getCanonicalFile());   
    
    }
   
  
  }

  private static Properties _loadProperties(CommandLine cmd, String strDefaultDir) throws FileNotFoundException, IOException {

    Properties props = new Properties();

    // Configuration file provided
    if (cmd.hasOption(OPT_CONFIG)) {
      
      String strConfiFileName = cmd.getOptionValue(OPT_CONFIG);
      
      System.out.println("Loading config from " + strConfiFileName );
      
      props.load(new FileInputStream(strConfiFileName));
      
      return props;
    }
    
    // There is a default file in aws folder
    File file = new File( strDefaultDir + "/awsokta.properties");
    
    if (file.exists()) {
      System.out.println("Found config at " + file.getCanonicalPath() );
      
      props.load(new FileInputStream(file) );
      return props;
    }
      
    // fall back on file in the classpath
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    InputStream resourceStream = loader.getResourceAsStream("awsokta.properties");

    props.load(resourceStream);

    return props;
  }

  /**
   * Initializes allowed command line options.
   * 
   * @return options
   */
  private static Options _initOptions() {
    Options options = new Options();

    options.addOption(new Option("h", OPT_HELP, false, "get usage help"));

    options.addOption(Option.builder("u").longOpt(OPT_USER).hasArg(true).argName("USER")
        .desc("OPTIONAL. User name; defaults to currently logged in user.").required(false).build());

    options.addOption(Option.builder("p").longOpt(OPT_PASSWORD).hasArg(true).argName("PASSWORD")
        .desc("Network/Windows user password. You will be prompted for one if not supplied.").build());

    options.addOption(Option.builder("org").longOpt(OPT_OKTA_ORG_DOMAIN).hasArg(true).argName("DOMAIN_NAME")
        .desc("Domain name for OKTA organization. Defaults to fngn.okta.com.").required(false).build());

    options.addOption(Option.builder("aws").longOpt(OPT_OKTA_AWS_URL).hasArg(true).argName("URL")
        .desc("Aws url as found on OKTA page under AWS chiclet. This or -e are required.").required(false)
        .build());

    options.addOption(Option.builder("e").longOpt(OPT_OKTA_AWS_ENV).hasArg(true).argName("ENV")
        .desc("Shortcut for specifying oktaAwsUrl. Supply one: dev,test,prod,corp,finr. Keys are configured via optional awsokta.properties").required(false)
        .build());

    options.addOption(Option.builder("c").longOpt(OPT_CONFIG).hasArg(true).argName("CONFIG")
        .desc("OPTIONAL. By default, look in /<user>/.aws for file named awsokta.properties").required(false).build());

    options.addOption(Option.builder("r").longOpt(OPT_ROLE_NAME).hasArg(true).argName("AWSROLE")
        .desc(
            "Specify aws role to assume in case multiple roles are available on AWS side. By default, first role in the list is assumed.")
        .required(false).build());

    options.addOption(Option.builder("s").longOpt(OPT_SILENCE).hasArg(false)
        .desc("OPTIONAL. Turn on silence mode to hide sensitive info.").required(false).build());

    options.addOption(Option.builder("f").longOpt(OPT_FILE).hasArg(true)
        .desc("OPTIONAL. Saves credentials in a custom file, instead of the default location (the '.aws' folder).").required(false).build());
    
    return options;
  }

  private static void _printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("java -jar <jar file>", "", options, "awsokta - 2016, Financial Engines", true);
  }

}
