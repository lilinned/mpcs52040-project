import net.sf.hajdbc.SimpleDatabaseClusterConfigurationFactory;
import net.sf.hajdbc.SynchronizationStrategy;
import net.sf.hajdbc.cache.eager.SharedEagerDatabaseMetaDataCacheFactory;
import net.sf.hajdbc.dialect.mysql.MySQLDialectFactory;
import net.sf.hajdbc.distributed.jgroups.JGroupsCommandDispatcherFactory;
import net.sf.hajdbc.durability.fine.FineDurabilityFactory;
import net.sf.hajdbc.sql.DriverDatabase;
import net.sf.hajdbc.sql.DriverDatabaseClusterConfiguration;
import net.sf.hajdbc.state.simple.SimpleStateManagerFactory;
import net.sf.hajdbc.sync.DumpRestoreSynchronizationStrategy;
import net.sf.hajdbc.sync.FastDifferentialSynchronizationStrategy;
import net.sf.hajdbc.sync.FullSynchronizationStrategy;
import net.sf.hajdbc.util.concurrent.cron.CronExpression;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.sql.*;
public class StockTicker {

  // JDBC driver name and database URL
   final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
   final String DB_URL = "jdbc:mysql://localhost/";

   //  Database credentials
   final String USER = "root";
   final String PASS = "0";

   // Database connection
   Connection conn = null;
//   Statement stmt = null;
//   PreparedStatement pstmt = null;

   private java.util.Date issue_time;
   private java.util.Date ctime;
   private SimpleDateFormat ft;
   public long time_offset = 0; // change to private later

   private String db_name = "DB_CHICAGO";
    private String db_name_raw;
   private String market_name = " ";
    private int max_iter = 90;

   private int trans_num;

    public StockTicker(String db_name, boolean load) throws ParseException, SQLException {
    this.db_name = "`" + db_name + "`";
        this.db_name_raw = db_name;
    String[] parts = db_name.split("_");
    this.market_name = "`" + parts[0] + "`";
    this.trans_num = 0;

    DatabaseInit();
        if (load) {
            createTransactiondb();
            LoadQTY_CSVData("data/qty_stocks.csv");
            LoadPRICE_CSVData("data/price_stocks.csv");
        }

    CreateStockData();
    initTIME();
    System.out.println("Start time: " + ft.format(ctime));
  }

  public int numofTransaction(){
    return this.trans_num;
  }

  //return transaction with tid
  public Transaction getTransaction(int tid) throws SQLException{
    String sql = "SELECT * FROM TRANSACTION WHERE TID = " + Integer.toString(tid);
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    if(!rs.next()){
      return null;
    }

    java.util.Date date = rs.getDate("DATE");
    String holder = rs.getString("HOLDER");
    String stock = rs.getString("STOCK");
    int amount = rs.getInt("AMOUNT");
    String type = rs.getString("TYPE");
    boolean status = rs.getBoolean("STATUS");

    Transaction returnT = new Transaction(date, tid, holder, stock, amount, type, status);
      stmt.close();
    return returnT;
  }

  //record transaction into database
  public void addTransaction(Transaction t) throws SQLException{

    // "CREATE TABLE TRANSACTION(" +
    //               "date DATETIME, " +
    //               "tid INTEGER, " +
    //               "holder VARCHAR(100), " +
    //               "stock VARCHAR(100), " +
    //               "amount INTEGER, " +
    //               "type VARCHAR(10), " +
    //               "status BOOLEAN)"

    // "VALUES (STR_TO_DATE(\'" + tokens[0] + " " + tokens[1] +":00\', \'%m/%d/%Y %H:%i:%s\')";
    String status = "TRUE";
    if(t.status){
      status = "TRUE";
    }
    else{
      status = "FALSE";
    }


      String sql = "INSERT INTO TRANSACTION (date, holder, stock, amount, type, status)" +
          "VALUES (" +
            "STR_TO_DATE(\'" + ft.format(t.date) + "\', \'%Y-%m-%d %H:%i:%s\'), " +
//            Integer.toString(t.tid) + ", " +
            "\'" + t.holder + "\', " +
            "\'" + t.stock + "\', " +
            Integer.toString(t.amount) + ", " +
            "\'" + t.type + "\', " +
            status + ")";
//    System.out.println(sql);
      Statement stmt = conn.createStatement();
    stmt.executeUpdate(sql);
      stmt.close();
  }



  private void databaseTest() throws SQLException{
    String sql = "SELECT SUM(QTY) AS DIFF FROM QUANTITY WHERE STOCK LIKE " +
          "\"ACCOR\" AND DATE > \"2016-01-01 08:00:00\" AND DATE < \"2016-01-01 08:00:00\"";
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    int diff = rs.getInt("diff");
    //System.out.println(Integer.toString(diff));
    rs.close();
      stmt.close();
  }

  public double getPrice(String stock) throws ParseException,SQLException{
    java.util.Date sys_time = new java.util.Date();
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);

    String sql = "SELECT PRICE FROM PRICE WHERE COMPANY LIKE \"" + stock + "\" AND DATE <= \"";
    sql = sql + ft.format(market_time) + "\" ORDER BY DATE DESC LIMIT 1";
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
      if (rs.next()) {
          ;
          Double result = rs.getDouble("PRICE");
          rs.close();
          stmt.close();
          return result;
    }
    else{
          stmt.close();
      return -1.0;    //did not find the price
    }
  }

  public int hasStock(String stock) throws ParseException, SQLException{
    java.util.Date sys_time = new java.util.Date();
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);
    syscTime(market_time);
    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);

    if(rs.next()){
        int result = rs.getInt("AMOUNT");
        rs.close();
        stmt.close();
        return result;
    }
    else{
        rs.close();
        stmt.close();
      return 0;
    }
  }

  public Return buyStocks(String holder, String stock, int amount) throws ParseException,SQLException{
    int new_amount;
    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    if(!rs.next()){
      rs.close();
        stmt.close();
      return new Return("Price : -1", false);
    }

    new_amount = rs.getInt("AMOUNT") - amount;
    rs.close();
    java.util.Date sys_time = new java.util.Date();
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);
    Transaction newT = new Transaction(market_time, this.trans_num, holder, stock, amount, "BUY", true);
    this.trans_num++;

    if(new_amount < 0){
      newT.status = false;
      addTransaction(newT);

        stmt.close();
      return new Return("not enough stocks", false);
    }
    else
    {
      sql = "UPDATE STOCKS SET AMOUNT = " + Integer.toString(new_amount) + " WHERE STOCK LIKE \"" + stock + "\"";
      stmt.executeUpdate(sql);
      double pr = getPrice(stock);
      addTransaction(newT);

        stmt.close();
        return new Return("Price : " + Double.toString(pr), true, pr);
    }
  }

  public double sellStocks(String holder, String stock, int amount) throws ParseException, SQLException{
//    int new_amount;7500
//
//    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
      Statement stmt = conn.createStatement();
//    ResultSet rs = stmt.executeQuery(sql);
//    rs.next();
//    new_amount = rs.getInt("AMOUNT") + amount;
      //sell always accept
      String sql = "UPDATE STOCKS SET AMOUNT = AMOUNT+" + Integer.toString(amount) + " WHERE STOCK LIKE \"" + stock + "\"";
      stmt.executeUpdate(sql);
//    rs.close();

    java.util.Date sys_time = new java.util.Date();
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);
    Transaction newT = new Transaction(market_time, this.trans_num, holder, stock, amount, "SELL", true);
    this.trans_num++;

    double pr = getPrice(stock);
    addTransaction(newT);
      stmt.close();
    return pr;
  }

  public double issueStocks(String stock, int amount) throws ParseException,SQLException{
    return sellStocks(stock, stock, amount);
  }

  public void syscTime(java.util.Date time) throws ParseException,SQLException{
    java.util.Date sys_time = new java.util.Date();
    time_offset = time.getTime() - sys_time.getTime();

    //sysc stock issue
    syscStockIssue(time);
    issue_time = time;
  }

  private void initTIME() throws SQLException,ParseException{
    ft = new SimpleDateFormat ("yyyy-MM-dd hh:mm:ss");
    ctime = ft.parse("2016-01-01 08:00:00");
    issue_time = ctime;
    syscTime(ctime);
  }

  private void syscStockIssue(java.util.Date now) throws ParseException, SQLException{
    String start = ft.format(issue_time);
    String end = ft.format(now);

    String fi_time = "2016-01-01 8:00:00";

    //get company name
    String sql = "SELECT COUNT(*) AS NUM FROM QUANTITY WHERE DATE = \"" + fi_time + "\"";
      Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    int num_stocks = rs.getInt("NUM");
    rs.close();

    String[] company_name = new String[num_stocks];
    int[] current_stock = new int[num_stocks];      //already issued STOCKS
    int[] issue_stock = new int[num_stocks];        //need to be issued at this time

    sql = "SELECT * FROM QUANTITY WHERE DATE = \"" + fi_time + "\"";
    rs = stmt.executeQuery(sql);

    int i = 0;
    while(rs.next()){
      company_name[i] = rs.getString("stock");
      i++;
    }
    rs.close();

    //get current amount of stocks and issued stocks
    for(i = 0; i < num_stocks; i++){
      sql = "SELECT * FROM STOCKS WHERE STOCK LIKE \"" + company_name[i] + "\"";
      rs = stmt.executeQuery(sql);
      rs.next();
      current_stock[i] = rs.getInt("amount");
      rs.close();

      sql = "SELECT SUM(QTY) AS DIFF FROM QUANTITY WHERE STOCK LIKE \"" + company_name[i] + "\" AND " +
            "DATE > \"" + start + "\" AND DATE <= \"" + end + "\"";
      rs = stmt.executeQuery(sql);
      rs.next();
      issue_stock[i] = rs.getInt("diff");
      rs.close();
    }

    //update stocks
    for(i = 0; i < num_stocks; i++){
      int newValue = current_stock[i] + issue_stock[i];
      sql = "UPDATE STOCKS SET AMOUNT = " + Integer.toString(newValue) + " WHERE STOCK LIKE \"" + company_name[i] + "\"";
      //System.out.println("syscStockIssue -- " + sql);
      stmt.executeUpdate(sql);
    }

      stmt.close();


  }

  private void CreateStockData(){
    try
    {
//        String sql = "USE " + db_name;
//        stmt.executeUpdate(sql);

        String sql = "DROP TABLE IF EXISTS STOCKS";
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(sql);

        sql = "CREATE TABLE STOCKS (" +
              "stock VARCHAR(100), amount INTEGER)";
        stmt.executeUpdate(sql);

        String fi_time = "2016-01-01 8:00:00";
        //insert first issue
        //get first issue company name

        sql = "SELECT COUNT(*) AS NUM FROM QUANTITY WHERE DATE = \"" + fi_time + "\"";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        int num_stocks = rs.getInt("NUM");
        rs.close();

        String[] company_name = new String[num_stocks];
        int[] amount = new int[num_stocks];

        sql = "SELECT * FROM QUANTITY WHERE DATE = \"" + fi_time + "\"";
        rs = stmt.executeQuery(sql);

        int i = 0;
        while(rs.next()){
          company_name[i] = rs.getString("stock");
          amount[i] = rs.getInt("qty");
          i++;
        }
        rs.close();

        for(i = 0; i < num_stocks; i++){
          sql = "INSERT INTO STOCKS " +
                "VALUES (\"" + company_name[i] + "\", " + Integer.toString(amount[i]) + ")";
          stmt.executeUpdate(sql);
        }
        stmt.close();

      } catch(SQLException se){
         //Handle errors for JDBC
         se.printStackTrace();
      } catch(Exception e){
         //Handle errors for Class.forName
         e.printStackTrace();
      }
  }

  private void createTransactiondb() throws SQLException{
//    String sql = "USE " + db_name;
//    stmt.executeUpdate(sql);

      String sql = "DROP TABLE IF EXISTS TRANSACTION";
      Statement stmt = conn.createStatement();
    stmt.executeUpdate(sql);

    sql =  "CREATE TABLE TRANSACTION(" +
                  "date DATETIME, " +
            "tid INTEGER AUTO_INCREMENT," +
                  "holder VARCHAR(100), " +
                  "stock VARCHAR(100), " +
                  "amount INTEGER, " +
                  "type VARCHAR(10), " +
            "status BOOLEAN, PRIMARY KEY (tid))";

    System.out.println("CreateTable sql : " + sql);

    stmt.executeUpdate(sql);
    System.out.println("Table created successfully...");
      stmt.close();
  }

  // public Transaction getTransaction(){}
  private void LoadPRICE_CSVData(String filename){

    try
    {
//        String sql = "USE " + db_name;
//        stmt.executeUpdate(sql);

        String sql = "DROP TABLE IF EXISTS PRICE";
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(sql);



        Scanner scanner = new Scanner(new File(filename));
        //scanner.useDelimiter(",");

        //read header
        //header1 - continent
        String header1 = scanner.nextLine();
        String[] parts1 = header1.split("\\,");

        //header2 - country
        String header2 = scanner.nextLine();
        String[] parts2 = header2.split("\\,");

        //header3 - market
        String header3 = scanner.nextLine();
        String[] parts3 = header3.split("\\,");
        for(int i = 0; i < parts3.length; i++){
          parts3[i] = "`" + parts3[i] + "`";
          // System.out.println(parts3[i]);
        }

        //header 4 - company
        String header4 = scanner.nextLine();
        String[] parts4 = header4.split("\\,");
        String[] parts5 = new String[parts4.length];
        for(int i = 0, j = 0; i < parts4.length; i++){
          if(parts4[i].charAt(0) != '\"'){
            parts5[j] = parts4[i];
            j++;
          }
          else{
            parts5[j] = parts4[i];
            int len = parts4[i].length();
            while(parts4[i].charAt(len - 1) != '\"'){
              i++;
              parts5[j] = parts5[j] + "," + parts4[i];
              len = parts4[i].length();
            }
            parts5[j] = parts5[j].replace("\"","");
            j++;
          }
        }


        sql =  "CREATE TABLE PRICE(" +
                      "date DATETIME, company VARCHAR(100), price DOUBLE)";

        System.out.println("CreateTable sql : " + sql);

        stmt.executeUpdate(sql);
        System.out.println("Table created successfully...");
        System.out.println("Import price data......");

        int c = 0;
        int icnt = 9;
        while (scanner.hasNextLine() && c < this.max_iter) {
            // System.out.print(scanner.nextLine()+"|||");
            String aLine = scanner.nextLine();
            String[] tokens = aLine.split("\\,");

            // -----------------------------------
            // this section for import information

            if(icnt == 9)
            {
              System.out.println("Date : " + tokens[0]);
              icnt = 0;
            }
            icnt++;
            c++;
            //this section for import information
            // -----------------------------------


            for(int i = 3; i < tokens.length; i++){
              String company_name = parts5[i];
              if(market_name.equals(parts3[i]))
              // System.out.print(tokens[i] + "\t");
            {
              sql = "INSERT INTO PRICE " +
                    "VALUES (STR_TO_DATE(\'" + tokens[0] + " " + tokens[1] +":00\', \'%m/%d/%Y %H:%i:%s\')";
              sql = sql + ", \"" + parts5[i];  //company name
              sql = sql + "\", " + tokens[i]; //
              sql = sql + ")";

              //System.out.println("sql: " + sql);
              stmt.executeUpdate(sql);
            }
            }

            // System.out.println(tokens[0] + " " + tokens[1]);
        }
        System.out.println("Success");
        scanner.close();
        stmt.close();
      } catch (FileNotFoundException e){
        System.err.println("cannot open file");
      } catch(SQLException se){
         //Handle errors for JDBC
         se.printStackTrace();
      } catch(Exception e){
         //Handle errors for Class.forName
         e.printStackTrace();
      }

  }


  private void LoadQTY_CSVData(String filename){
    try
    {
//        String sql = "USE " + db_name;
//        stmt.executeUpdate(sql);

        String sql = "DROP TABLE IF EXISTS QUANTITY";
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(sql);



        Scanner scanner = new Scanner(new File(filename));
        //scanner.useDelimiter(",");

        //read header
        //header1 - continent
        String header1 = scanner.nextLine();
        String[] parts1 = header1.split("\\,");
        System.out.println("header1 done" + parts1[3]);

        //header2 - country
        String header2 = scanner.nextLine();
        String[] parts2 = header2.split("\\,");
        System.out.println("header2 done" + parts2[3]);

        //header3 - market
        String header3 = scanner.nextLine();
        String[] parts3 = header3.split("\\,");
        for(int i = 0; i < parts3.length; i++){
          parts3[i] = "`" + parts3[i] + "`";
          // System.out.println(parts3[i]);
        }
        System.out.println("header3 done" + parts3[3]);

        //header 4 - company
        String header4 = scanner.nextLine();
        String[] parts4 = header4.split("\\,");
        String[] parts5 = new String[parts4.length];
        for(int i = 0, j = 0; i < parts4.length; i++){
          if(parts4[i].charAt(0) != '\"'){
            parts5[j] = parts4[i];
            j++;
          }
          else{
            parts5[j] = parts4[i];
            int len = parts4[i].length();
            while(parts4[i].charAt(len - 1) != '\"'){
              i++;
              parts5[j] = parts5[j] + "," + parts4[i];
              len = parts4[i].length();
            }
            parts5[j] = parts5[j].replace("\"","");
            j++;
          }
        }
        System.out.println("header4 done" + parts4[3]);
        for(int i = 0; i < parts5.length; i++){
          System.out.println(parts5[i]);
        }

        sql =  "CREATE TABLE QUANTITY(" +
                      "date DATETIME, stock VARCHAR(100), qty INTEGER)";

        System.out.println("CreateTable sql : " + sql);

        stmt.executeUpdate(sql);
        System.out.println("Table created successfully...");

        while(scanner.hasNextLine()){
            // System.out.print(scanner.nextLine()+"|||");
            String aLine = scanner.nextLine();
            String[] tokens = aLine.split("\\,");
            for(int i = 3; i < tokens.length; i++){
              System.out.println(market_name + " " + parts3[i]);
              if(market_name.equals(parts3[i]) && tokens[i].length() != 0)
              {
                sql = "INSERT INTO QUANTITY " +
                      "VALUES (STR_TO_DATE(\'" + tokens[0] + " " + tokens[1] +":00\', \'%m/%d/%Y %H:%i:%s\')";
                  sql = sql + ", \"" + parts5[i];  //company name
                  sql = sql + "\", " + tokens[i]; //
                sql = sql + ")";

                // System.out.println("sql: " + sql);
                stmt.executeUpdate(sql);
              }
            }
        }
        scanner.close();
        stmt.close();
      } catch (FileNotFoundException e){
        System.err.println("cannot open file");
      } catch(SQLException se){
         //Handle errors for JDBC
         se.printStackTrace();
      } catch(Exception e){
         //Handle errors for Class.forName
         e.printStackTrace();
      }

  }

  //connect and create database
  private void DatabaseInit(){
    conn = null;
     try{
        //STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
         System.out.println("Creating database...");
         conn = DriverManager.getConnection(DB_URL, USER, PASS);
         Statement stmt = conn.createStatement();
         List<DriverDatabase> lst = new ArrayList<>();
         for (int i = 0; i < 5; i++) {
             String name = this.db_name_raw + "_" + i;
//             stmt.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
             stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name + "`");
             DriverDatabase db = new DriverDatabase();
             db.setId(name);
             db.setLocation("jdbc:mysql://localhost:3306/" + name);
             db.setUser("root");
             db.setPassword("0");
             lst.add(db);
         }
         stmt.close();
         conn.close();
//        DriverDatabase db1 = new DriverDatabase();
//        db1.setId("db1");
//        db1.setLocation("jdbc:mysql://localhost:3306/db1");
//        db1.setUser("root");
//        db1.setPassword("0");
//
//        DriverDatabase db2 = new DriverDatabase();
//        db2.setId("db2");
//        db2.setLocation("jdbc:mysql://localhost:3306/db2");
//        db2.setUser("root");
//        db2.setPassword("0");

         // Define the cluster configuration itself
         DriverDatabaseClusterConfiguration config = new DriverDatabaseClusterConfiguration();
         // Specify the database composing this cluster
         config.setDatabases(lst);
         // Define the dialect
         config.setDialectFactory(new MySQLDialectFactory());
         // Don't cache any meta data
         config.setDatabaseMetaDataCacheFactory(new SharedEagerDatabaseMetaDataCacheFactory());
         // Use an in-memory state manager
         config.setStateManagerFactory(new SimpleStateManagerFactory());
         // Make the cluster distributable
         config.setDispatcherFactory(new JGroupsCommandDispatcherFactory());
         // Activate every minute
         config.setAutoActivationExpression(new CronExpression("0 0/1 * 1/1 * ? *"));
         // Strategy
         config.setDurabilityFactory(new FineDurabilityFactory());
         Map<String, SynchronizationStrategy> map = new Hashtable<>();
         map.put("dump-restore", new DumpRestoreSynchronizationStrategy());
         map.put("full", new FullSynchronizationStrategy());
         map.put("diff", new FastDifferentialSynchronizationStrategy());
         config.setSynchronizationStrategyMap(map);
         config.setDefaultSynchronizationStrategy("full");
         config.setFailureDetectionExpression(new CronExpression("0 0/1 * 1/1 * ? *"));

         // Register the configuration with the HA-JDBC driver
         net.sf.hajdbc.sql.Driver.setConfigurationFactory(this.db_name_raw, new SimpleDatabaseClusterConfigurationFactory<Driver, DriverDatabase>(config));
         // Database cluster is now ready to be used!
         conn = DriverManager.getConnection("jdbc:ha-jdbc:" + this.db_name_raw, "root", "0");
        //STEP 3: Open a connection
//        System.out.println("Connecting to database...");
//        conn = DriverManager.getConnection(DB_URL, USER, PASS);

        //STEP 4: Execute a query
//        stmt = conn.createStatement();

//        String sql = "DROP DATABASE IF EXISTS " + db_name;
//        stmt.executeUpdate(sql);

//        String sql = "CREATE DATABASE IF NOT EXISTS " + db_name;
//        stmt.executeUpdate(sql);
//         this.stmt.executeUpdate("USE " + db_name);

        System.out.println("Database created successfully...");
     }catch(SQLException se){
        //Handle errors for JDBC
        se.printStackTrace();
     }catch(Exception e){
        //Handle errors for Class.forName
        e.printStackTrace();
     }
    }
}
