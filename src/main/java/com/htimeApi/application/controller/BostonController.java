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
@RequestMapping("/boston")
public class BostonController {

    String feedId = "mbta/64";
    String feedVersion;

    DSLContext connect() throws SQLException {
        java.sql.Connection conn = DriverManager.getConnection("jdbc:mysql://database-1.c2skpltdp2me.us-east-2.rds.amazonaws.com:3306/gtfs?autoReconnect=true&useSSL=false&useUnicode=true&useLegacyDatetimeCode=false&autoCommit=false&relaxAutoCommit=true", "api", "apipassword"); //In earlier version I was causing the time zones to be switched without warrant.
        Configuration conf = new DefaultConfiguration().set(conn).set(SQLDialect.MYSQL_8_0);
        ConnectionImpl cImpl = (ConnectionImpl)conf.connectionProvider().acquire();
        cImpl.getSession().getServerSession().setAutoCommit(false);
        Configuration conf2 = new DefaultConfiguration().set(cImpl).set(SQLDialect.MYSQL_8_0);
        DSLContext dsl = DSL.using(conf2);
        return dsl;
    }

    @RequestMapping("/routes")
    @ResponseBody
    public List<Map<String, Object>> routes(@RequestParam(value = "name", required = false) String name ) {

        DSLContext dsl;
        try{
            dsl = this.connect();
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

            if (name != null){
                return dsl.selectFrom(ROUTE).where(ROUTE.DEFAULT_NAME.like("%"+name+"%")).and(ROUTE.FEED_VERSION.eq(this.feedVersion)).fetchMaps();

            }else{
                return dsl.selectFrom(ROUTE).where(ROUTE.FEED_VERSION.eq(this.feedVersion)).fetchMaps();
            }

        }catch(Exception e){
            throw e;
        }finally{
            dsl.close();
        }
    }

    //todo too much copying and pasting


    @RequestMapping("/stops")
    @ResponseBody
    public List<Map<String, Object>> stops(@RequestParam(value= "routeId", required = false) String routeId, @RequestParam(value = "name", required = false) String name ) {
        DSLContext dsl;
        try{
            dsl = this.connect();
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
            if (routeId != null){
                ft.setRoute(routeId);
            }
            if (name != null){
                return ft.getStopsMaps(name);//todo return more info from this method
            }else return ft.getStopsMaps("");
        }catch(Exception e){
            throw e;
        }finally{
            dsl.close();
        }


    }

    @RequestMapping("/dests")
    @ResponseBody
    public List<Map<String, Object>> dests(@RequestParam(value= "origin") String origin, @RequestParam(value= "routeId", required = false) String routeId, @RequestParam(value = "name", required = false) String name ) {
        DSLContext dsl;
        try{
            dsl = this.connect();
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

            ft.setOrigin(origin);

            if (routeId != null){
                ft.setRoute(routeId);
            }
            if (name != null){
                return ft.getDestinationsMaps(name);
            } else return ft.getDestinationsMaps("");
        }catch(Exception e){
            throw e;
        }finally{
            dsl.close();
        }
    }

    @RequestMapping("/timetable")
    @ResponseBody
    public Map<String,Object> timetable(@RequestParam(value= "origin") String origin, @RequestParam(value= "dest") String dest, @RequestParam(value= "route", required = false) String routeId, @RequestParam(value= "year") int year, @RequestParam(value= "month") int month, @RequestParam(value= "date") int date) {
        DSLContext dsl;
        try{
            dsl = this.connect();
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
            ft.setDestination(dest);
            ft.setDate(year, month, date);

            if (routeId !=  null){
                ft.setRoute(routeId);
                result.put("route", dsl.selectFrom(ROUTE).where(ROUTE.ROUTE_ID.eq(routeId)).and(ROUTE.FEED_VERSION.eq(this.feedVersion)).fetchOneMap());
            }

            result.put("origin", dsl.selectFrom(STOP).where(STOP.STOP_ID.eq(origin)).and(STOP.FEED_VERSION.eq(this.feedVersion)).fetchOneMap());
            result.put("destination", dsl.selectFrom(STOP).where(STOP.STOP_ID.eq(dest)).and(STOP.FEED_VERSION.eq(this.feedVersion)).fetchOneMap());
            result.put("result", ft.getTimetableMaps());
            result.put("date", ft.dateString());
            return result;
            //todo make sure to specify feed_version
        }catch(Exception e){
            throw e;
        }finally{
            dsl.close();
        }

    }

//    @ModelAttribute
//    public void setVaryResponseHeader(HttpServletResponse response) {
//        response.setHeader("Access-Control-Allow-Origin", "*");
//    }

}