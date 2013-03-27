package com.anjlab.csv2db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

import com.anjlab.csv2db.Configuration.CSVOptions;
import com.anjlab.csv2db.Configuration.OperationMode;
import com.google.gson.Gson;

public class ImporterTest
{
    @Test
    public void createTestConfig() throws Exception
    {
        Configuration config = new Configuration();
        
        Map<Integer, String> columnMappings = new HashMap<Integer, String>();
        columnMappings.put(0, "company_name");
        columnMappings.put(1, "company_number");
        columnMappings.put(4, "address_line_1");
        columnMappings.put(5, "address_line_2");
        
        config.setColumnMappings(columnMappings);
        
        Map<String, String> connectionProperties = new HashMap<String, String>();
        connectionProperties.put("username", "sa");
        connectionProperties.put("password", "");
        
        config.setConnectionProperties(connectionProperties);

        config.setConnectionUrl("jdbc:derby:memory:myDB;create=true");
        config.setDriverClass("org.apache.derby.jdbc.EmbeddedDriver");
        
        CSVOptions csvOptions = new CSVOptions();
        csvOptions.setSkipLines(1);
        csvOptions.setEscapeChar('\b');
        config.setCsvOptions(csvOptions);
        config.setOperationMode(OperationMode.MERGE);
        config.setPrimaryKeys(Arrays.asList("company_number"));
        config.setTargetTable("companies_house_records");
        
        Map<String, String> defaultValues = new HashMap<String, String>();
        defaultValues.put("id", "current_timestamp");
        config.setDefaultValues(defaultValues);
        
        String expectedJson = new Gson().toJson(config);
        
        String actualJson =
                new Gson().toJson(
                        Configuration.fromJson("src/test/resources/test-config.json"));
        
        Assert.assertEquals(expectedJson, actualJson);
    }
    
    @Test
    public void testImport() throws Exception
    {
        Configuration config = Configuration.fromJson(
                "src/test/resources/test-config.json");
        
        config.getCsvOptions().setEscapeChar((char) 0);
        
        Importer importer = new Importer(config);
        importer.setAutocloseConnection(false);
        
        Connection connection = importer.getConnection();
        
        connection.createStatement()
                  .executeUpdate(
                      "create table companies_house_records (" +
                          "id timestamp not null," +
                          "company_name varchar(160)," +
                          "company_number varchar(8)," +
                          "address_line_1 varchar(300)," +
                          "address_line_2 varchar(300)" +
                      ")");
        
        importer.performImport("src/test/resources/test-data.csv");
        
        assertRecordCount(connection, getExpectedDataset());
        
        importer.performImport("src/test/resources/test-data.csv");
        
        assertRecordCount(connection, getExpectedDataset());
        
        connection.close();
        
        config.setOperationMode(OperationMode.INSERT);
        config.setBatchSize(3);
        
        importer = new Importer(config);
        importer.setAutocloseConnection(false);
        
        connection = importer.getConnection();
        
        importer.performImport("src/test/resources/test-data.csv");
        
        List<Object[]> expectedDataset = new ArrayList<Object[]>();
        expectedDataset.addAll(getExpectedDataset());
        expectedDataset.addAll(getExpectedDataset());
        assertRecordCount(connection, expectedDataset);
    }

    private List<Object[]> getExpectedDataset()
    {
        return Arrays.asList(new Object[] {"! LTD", "08209948", "METROHOUSE 57 PEPPER ROAD", "HUNSLET"},
                      new Object[] {"!BIG IMPACT GRAPHICS LIMITED", "07382019", "335 ROSDEN HOUSE", "372 OLD STREET"},
                      new Object[] {"!NFERNO LTD.", "04753368", "FIRST FLOOR THAVIES INN HOUSE 3-4", "HOLBORN CIRCUS"},
                      new Object[] {"!NSPIRED LTD", "SC421617", "12 BON ACCORD SQUARE", ""},
                      new Object[] {"!OBAC INSTALLATIONS LIMITED", "07527820", "DEVONSHIRE HOUSE", "60 GOSWELL ROAD"},
                      new Object[] {"!OBAC UK LIMITED", "07687209", "DEVONSHIRE HOUSE", "60 GOSWELL ROAD"},
                      new Object[] {"!ST MEDIA SOUTHAMPTON LTD", "07904170", "10 NORTHBROOK HOUSE", "FREE STREET, BISHOPS WALTHAM"},
                      new Object[] {"ALJOH B.V.", "SF000899", "ALEXANDER HINSHELWOOD BARR", "\"SHALIMAR\""},
                      new Object[] {"ALLEGIS SERVICES (INDIA) PRIVATE LIMITED","FC027847","\\54, 1ST MAIN ROAD","3RD PHASE"},
                      new Object[] {"APS DIRECT LIMITED","05638208","MANOR COURT CHAMBERS \\","126 MANOR COURT ROAD"});
    }

    protected void assertRecordCount(Connection connection, List<Object[]> expectedData) throws SQLException
    {
        ResultSet resultSet;
        resultSet = connection.createStatement()
            .executeQuery("SELECT * FROM companies_house_records");
        
        int index = 0;
        while (resultSet.next())
        {
            int columnCount = resultSet.getMetaData().getColumnCount();
            for (int i = 2; i <= columnCount; i++)
            {
                Object columnValue = resultSet.getObject(i);
                
                Assert.assertEquals(expectedData.get(index)[i - 2], columnValue);
            }
            index++;
        }
        resultSet.close();
    }
}
