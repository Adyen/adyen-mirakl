package com.adyen.mirakl.cucumber.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ReadConfig {

    private static Properties properties = new Properties();
    public static String readConfig(String key) {
        try {
            InputStream input = new FileInputStream("/home/admin/workspace/SpiceAutomationTests/src/test/resources/properties/config.properties");
            properties.load(input);

        } catch (IOException io) {
            io.printStackTrace();
        }
        return properties.getProperty(key);
    }
}
