import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.sql.*;

public class StockTicker {

  // JDBC driver name and database URL
   static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
   static final String DB_URL = "jdbc:mysql://localhost/";

   //  Database credentials
   static final String USER = "root";
   static final String PASS = "0";

   // Database connection
   static Connection conn = null;
   static Statement stmt = null;
   static PreparedStatement pstmt = null;

   static private java.util.Date issue_time;
   static private java.util.Date ctime;
   static private SimpleDateFormat ft;
   static private long time_offset = 0;

   static private String db_name = "DB_CHICAGO";

  //private static HashMap<Integer, Transaction> recentTransaction;
  private static int idCounter;

  public static void main(String[] args) throws ParseException, SQLException {
    DatabaseInit();
    LoadQTY_CSVData("qty_stocks.csv");
    LoadPRICE_CSVData("price_stocks.csv");
    CreateStockData();
    initTIME();
    System.out.println("Now is: " + ft.format(ctime));
    double pr;

    pr = getPrice("ACCOR");
    System.out.println("ACCOR PRICE: " + Double.toString(pr));
    System.out.println("buy ACCOR 100 :" + buyStocks("ACCOR", 100).message);
    System.out.println("buy ACCOR 100 :" + buyStocks("ACCOR", 100).message);
    syscTime(ft.parse("2016-01-01 10:00:01"));
    pr = getPrice("ACCOR");
    System.out.println("ACCOR PRICE: " + Double.toString(pr));
    syscTime(ft.parse("2016-01-01 11:00:01"));
    pr = getPrice("ACCOR");
    System.out.println("ACCOR PRICE: " + Double.toString(pr));

    databaseTest();

    System.out.println("123 : " + Integer.toString(hasStock("123")));
    System.out.println("ACCOR : " + Integer.toString(hasStock("ACCOR")));

    System.out.println("buy ACCOR 100 :" + buyStocks("ACCOR", 100).message);
    System.out.println("buy ACCOR 100 :" + buyStocks("ACCOR", 100).message);
    System.out.println("sell ACCOR 100 :" + sellStocks("ACCOR", 100).message);
    System.out.println("buy ACCOR 100 :" + buyStocks("ACCOR", 100).message);
    System.out.println("buy ACCOR 300 :" + buyStocks("ACCOR", 300).message);
    System.out.println("sell ACCOR 300 :" + sellStocks("ACCOR", 300).message);
    System.out.println("buy ACCOR 300 :" + buyStocks("ACCOR", 300).message);

  }

  private static void databaseTest() throws SQLException{
    String sql = "SELECT SUM(QTY) AS DIFF FROM QUANTITY WHERE STOCK LIKE " +
          "\"ACCOR\" AND DATE > \"2016-01-01 08:00:00\" AND DATE < \"2016-01-01 08:00:00\"";
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    int diff = rs.getInt("diff");
    System.out.println(Integer.toString(diff));
    rs.close();
  }

  private static double getPrice(String stock) throws ParseException,SQLException{
    java.util.Date sys_time = new java.util.Date();
    System.out.println("getPrice -- actualTime : " + ft.format(sys_time));
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);
    System.out.println("getPrice -- marketTIME : " + ft.format(market_time));

    String sql = "SELECT PRICE FROM PRICE WHERE COMPANY LIKE \"" + stock + "\" AND DATE <= \"";
    sql = sql + ft.format(market_time) + "\" ORDER BY DATE DESC LIMIT 1";
    ResultSet rs = stmt.executeQuery(sql);
    if(rs.next())
    {
      return rs.getDouble("PRICE");
    }
    else{
      return -1.0;    //did not find the price
    }
  }

  public static int hasStock(String stock) throws ParseException, SQLException{
    java.util.Date sys_time = new java.util.Date();
    System.out.println("actualTime : " + ft.format(sys_time));
    java.util.Date market_time = new java.util.Date(sys_time.getTime() + time_offset);
    System.out.println("marketTIME : " + ft.format(market_time));

    syscTime(market_time);
    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
    ResultSet rs = stmt.executeQuery(sql);

    if(rs.next()){
      return rs.getInt("AMOUNT");
    }
    else{
      return 0;
    }
  }

  public static Return buyStocks(String stock, int amount) throws ParseException,SQLException{
    int new_amount;

    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    new_amount = rs.getInt("AMOUNT") - amount;
    rs.close();

    if(new_amount < 0){
      return new Return("not enough stocks", false);
    }
    else
    {
      sql = "UPDATE STOCKS SET AMOUNT = " + Integer.toString(new_amount) + " WHERE STOCK LIKE \"" + stock + "\"";
      stmt.executeUpdate(sql);
      double pr = getPrice(stock);
      return new Return("Price : " + Double.toString(pr), true);
    }
  }

  public static Return sellStocks(String stock, int amount) throws ParseException, SQLException{
    int new_amount;

    String sql = "SELECT AMOUNT FROM STOCKS WHERE STOCK LIKE \"" + stock + "\"";
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    new_amount = rs.getInt("AMOUNT") + amount;
    rs.close();

    //sell always accept
    sql = "UPDATE STOCKS SET AMOUNT = " + Integer.toString(new_amount) + " WHERE STOCK LIKE \"" + stock + "\"";
    stmt.executeUpdate(sql);

    double pr = getPrice(stock);
    return new Return("Price : " + Double.toString(pr), true);
  }

  public static Return issueStocks(String stock, int amount) throws ParseException,SQLException{
    return sellStocks(stock, amount);
  }

  public static void syscTime(java.util.Date time) throws ParseException,SQLException{
    java.util.Date sys_time = new java.util.Date();
    System.out.println("syscTime   : " + ft.format(time));
    System.out.println("actualTime : " + ft.format(sys_time));
    time_offset = time.getTime() - sys_time.getTime();
    System.out.println(time_offset);

    //sysc stock issue
    syscStockIssue(time);
    issue_time = time;
  }

  private static void initTIME() throws SQLException,ParseException{
    ft = new SimpleDateFormat ("yyyy-MM-dd hh:mm:ss");
    ctime = ft.parse("2016-01-01 08:00:00");
    issue_time = ctime;
    syscTime(ctime);
  }

  private static void syscStockIssue(java.util.Date now) throws ParseException, SQLException{
    String start = ft.format(issue_time);
    String end = ft.format(now);

    String fi_time = "2016-01-01 8:00:00";

    //get company name
    String sql = "SELECT COUNT(*) AS NUM FROM QUANTITY WHERE DATE = \"" + fi_time + "\"";
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    int num_stocks = rs.getInt("NUM");
    System.out.println(Integer.toString(num_stocks));
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
      System.out.println(sql);
      stmt.executeUpdate(sql);
    }


  }

  private static void CreateStockData(){
    try
    {
        String sql = "USE " + db_name;
        stmt.executeUpdate(sql);

        sql = "DROP TABLE IF EXISTS STOCKS";
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
        System.out.println(Integer.toString(num_stocks));
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
          System.out.println(company_name[i] + " :" + Integer.toString(amount[i]));
          sql = "INSERT INTO STOCKS " +
                "VALUES (\"" + company_name[i] + "\", " + Integer.toString(amount[i]) + ")";
          stmt.executeUpdate(sql);
        }

      } catch(SQLException se){
         //Handle errors for JDBC
         se.printStackTrace();
      } catch(Exception e){
         //Handle errors for Class.forName
         e.printStackTrace();
      }
  }

  // public Transaction getTransaction(){}
  private static void LoadPRICE_CSVData(String filename){

    try
    {
        String sql = "USE " + db_name;
        stmt.executeUpdate(sql);

        sql = "DROP TABLE IF EXISTS PRICE";
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

        while(scanner.hasNextLine()){
            // System.out.print(scanner.nextLine()+"|||");
            String aLine = scanner.nextLine();
            String[] tokens = aLine.split("\\,");
            for(int i = 3; i < tokens.length; i++){
              String company_name = parts5[i];
              if(company_name.equals("ACCOR") || company_name.equals("AIR LIQUIDE") || company_name.equals("AIRBUS GROUP"))
              // System.out.print(tokens[i] + "\t");
            {
              sql = "INSERT INTO PRICE " +
                    "VALUES (STR_TO_DATE(\'" + tokens[0] + " " + tokens[1] +":00\', \'%m/%d/%Y %H:%i:%s\')";
              sql = sql + ", \"" + parts5[i];  //company name
              sql = sql + "\", " + tokens[i]; //
              sql = sql + ")";

              // System.out.println("sql: " + sql);
              stmt.executeUpdate(sql);
            }
            }

            // System.out.println(tokens[0] + " " + tokens[1]);
        }
        System.out.println("Table Price build successfully");
        scanner.close();
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


  private static void LoadQTY_CSVData(String filename){
    try
    {
        String sql = "USE " + db_name;
        stmt.executeUpdate(sql);

        sql = "DROP TABLE IF EXISTS QUANTITY";
        stmt.executeUpdate(sql);



        Scanner scanner = new Scanner(new File(filename));
        //scanner.useDelimiter(",");

        //read header
        String header = scanner.nextLine();
        String[] parts = header.split("\\,");

        //parts[0] : Date
        //parts[1] : Time
        //parts[2] : Stock

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
              // System.out.print(tokens[i] + "\t");
              sql = "INSERT INTO QUANTITY " +
                    "VALUES (STR_TO_DATE(\'" + tokens[0] + " " + tokens[1] +":00\', \'%m/%d/%Y %H:%i:%s\')";
              sql = sql + ", \'" + parts[i];  //company name
              sql = sql + "\', " + tokens[i]; //
              sql = sql + ")";

              // System.out.println("sql: " + sql);
              stmt.executeUpdate(sql);
            }
        }
        scanner.close();
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
  private static void DatabaseInit(){
    conn = null;
    stmt = null;
     try{
        //STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");

        //STEP 3: Open a connection
        System.out.println("Connecting to database...");
        conn = DriverManager.getConnection(DB_URL, USER, PASS);

        //STEP 4: Execute a query
        System.out.println("Creating database...");
        stmt = conn.createStatement();

        String sql = "DROP DATABASE IF EXISTS " + db_name;
        stmt.executeUpdate(sql);

        sql = "CREATE DATABASE " + db_name;
        stmt.executeUpdate(sql);

        System.out.println("Database created successfully...");
     }catch(SQLException se){
        //Handle errors for JDBC
        se.printStackTrace();
     }catch(Exception e){
        //Handle errors for Class.forName
        e.printStackTrace();
     }
     System.out.println("Goodbye!");
    }
}
