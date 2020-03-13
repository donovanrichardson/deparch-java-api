package com.htimeApi.application.controller;

import com.mysql.cj.jdbc.ConnectionImpl;
import com.htimeApi.java.dbAccess.table.FeedTable;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static com.schema.tables.Route.ROUTE;
import static com.schema.tables.Stop.STOP;
import static com.schema.tables.Feed.FEED;

@Controller
@RequestMapping("/lirr")
public class LIRRController {

    String feedId = "mta/86";
    String feedVersion;

    class DSLWrapper{
        DSLContext dsl;
        java.sql.Connection conn;
        DSLWrapper() throws SQLException{
            String password = System.getenv("DEPARCH");
            this.conn = DriverManager.getConnection("jdbc:mysql://database-1.c2skpltdp2me.us-east-2.rds.amazonaws.com:3306/gtfs?autoReconnect=true&useSSL=false&useUnicode=true&useLegacyDatetimeCode=false&autoCommit=false&relaxAutoCommit=true", "api", password); //In earlier version I was causing the time zones to be switched without warrant.
            Configuration conf = new DefaultConfiguration().set(conn).set(SQLDialect.MYSQL_8_0);
            ConnectionImpl cImpl = (ConnectionImpl)conf.connectionProvider().acquire();
            cImpl.getSession().getServerSession().setAutoCommit(false);
            Configuration conf2 = new DefaultConfiguration().set(cImpl).set(SQLDialect.MYSQL_8_0);
            this.dsl = DSL.using(conf2);
        }
    }


    @RequestMapping("/stops")
    @ResponseBody
    public List<Map<String, Object>> stops(@RequestParam(value = "name", required = false) String name ) {
        DSLContext dsl;
        DSLWrapper w;
        try{
            w = new DSLWrapper();
            dsl = w.dsl;
        } catch (SQLException e) {
            e.printStackTrace();
            List error = new ArrayList<Map<String, Object>>();
            Map<String, Object> info = new HashMap();
            info.put("error", "unable to connect to database");
            error.add(info);
            return error;
        }

        try{

            this.feedVersion = dsl.select(FEED.LATEST).from(FEED).where(FEED.ID.eq(this.feedId)).fetchOne(FEED.LATEST);

            FeedTable ft = new FeedTable(this.feedVersion, dsl);
            List<Map<String,Object>> res;
            if (name != null){
                res = ft.getStopsMaps(name);//todo return more info from this method
            }else res = ft.getStopsMaps("");
            w.conn.close();
            return res;
            //todo make sure to specify feed_version
        }catch(SQLException e){
            List<Map<String,Object>> excl = new ArrayList();
            Map<String,Object> exc = new HashMap<>();
            exc.put("exception", "SQLException");
            excl.add(exc);
            return excl;
        }finally{
            dsl.close();
        }


    }

    @RequestMapping("/timetable")
    @ResponseBody
    public Map<String,Object> timetable(@RequestParam(value= "origin") String origin,  @RequestParam(value= "year") int year, @RequestParam(value= "month") int month, @RequestParam(value= "date") int date) throws SQLException {
        DSLContext dsl;
        DSLWrapper w = null;
        try{
            w = new DSLWrapper();
            dsl = w.dsl;
        } catch (SQLException e) {
            e.printStackTrace();
//            List error = new ArrayList<Map<String, Object>>();
            Map<String, Object> info = new HashMap();
            info.put("error", "unable to connect to database");
//            error.add(info);
            return info;
        }

        try{
            Map<String,Object> result = new HashMap<>();

            this.feedVersion = dsl.select(FEED.LATEST).from(FEED).where(FEED.ID.eq(this.feedId)).fetchOne(FEED.LATEST);

            FeedTable ft = new FeedTable(this.feedVersion, dsl);

            ft.setOrigin(origin);
            ft.setDate(year, month, date);


            result.put("origin", dsl.selectFrom(STOP).where(STOP.STOP_ID.eq(origin)).and(STOP.FEED_VERSION.eq(this.feedVersion)).fetchOneMap());
            result.put("result", ft.getLIRRMaps());
            result.put("date", ft.dateString());
            w.conn.close();
            return result;
            //todo make sure to specify feed_version
        }catch(SQLException e){
            Map<String,Object> exc = new HashMap<>();
            exc.put("exception", "SQLException");
            return exc;
        }finally{
            dsl.close();
        }

    }

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "https://donovanrichardson.github.io");
    }

}