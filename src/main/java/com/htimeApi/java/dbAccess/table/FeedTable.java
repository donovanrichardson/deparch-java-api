package com.htimeApi.java.dbAccess.table;

import com.schema.tables.StopTime;
import com.schema.tables.records.ServiceRecord;
import com.schema.tables.records.StopTimeRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;

import java.util.*;

import static com.schema.tables.Route.ROUTE;
import static com.schema.tables.Service.SERVICE;
import static com.schema.tables.ServiceException.SERVICE_EXCEPTION;
import static com.schema.tables.Stop.STOP;
import static com.schema.tables.StopTime.STOP_TIME;
import static com.schema.tables.Trip.TRIP;

public class FeedTable {

    private String feedVersion;
    private DSLContext dsl;
    private Calendar date;
    private String origin;
    private String route = null;
    private String dest;
    private Map<Integer, TableField<ServiceRecord, Byte>> weekdays = new HashMap();
    public static JSONFormat jf = new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT);

    public FeedTable(String feedVersion, DSLContext dsl){
        this.feedVersion = feedVersion;
        this.dsl = dsl;
        weekdays.put(Calendar.MONDAY, SERVICE.MONDAY);
        weekdays.put(Calendar.TUESDAY, SERVICE.TUESDAY);
        weekdays.put(Calendar.WEDNESDAY, SERVICE.WEDNESDAY);
        weekdays.put(Calendar.THURSDAY, SERVICE.THURSDAY);
        weekdays.put(Calendar.FRIDAY, SERVICE.FRIDAY);
        weekdays.put(Calendar.SATURDAY, SERVICE.SATURDAY);
        weekdays.put(Calendar.SUNDAY, SERVICE.SUNDAY);
    }

    public List<Map<String, Object>> getStopsMaps(String like) {
        SelectConditionStep notYetMaps = this.applyRouteStops(dsl.selectDistinct(STOP_TIME.STOP_ID, STOP.STOP_NAME).from(STOP_TIME.leftJoin(STOP).on(STOP.STOP_ID.eq(STOP_TIME.STOP_ID)))
                .where(STOP.STOP_NAME.like("%"+like+"%"))
                .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion)));
        return notYetMaps.fetchMaps();
    }

    public List<Map<String, Object>> getDestinationsMaps(String like) {
        StopTime subq = STOP_TIME.as("sq");
        SelectSeekStep1 req = this.applyRoute(dsl.selectDistinct(STOP_TIME.STOP_ID, STOP.STOP_NAME).from(STOP_TIME.leftJoin(STOP).on(STOP.STOP_ID.eq(STOP_TIME.STOP_ID)))
                .whereExists(dsl.selectFrom(subq)
                        .where(subq.STOP_ID.eq(this.origin))
                        .and(STOP_TIME.STOP_SEQUENCE.greaterThan(subq.STOP_SEQUENCE))
                        .and(STOP_TIME.TRIP_ID.eq(subq.TRIP_ID))
                        .and(subq.FEED_VERSION.eq(this.feedVersion)))
                .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion)))
                .and(STOP.STOP_NAME.like("%"+like+"%")).orderBy(STOP.STOP_NAME.asc());
        return req.fetchMaps();
    }

    public List<Map<String, Object>> getTimetableMaps() {
        StopTime subq = STOP_TIME.as("subq");

        SelectConditionStep<StopTimeRecord> withoutRoute =
                dsl.selectFrom(STOP_TIME)
                        .where(STOP_TIME.STOP_ID.eq(origin))
                        .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion))
                        .andExists(dsl.selectFrom(subq)
                                .where(STOP_TIME.TRIP_ID.eq(subq.TRIP_ID))
                                .and(STOP_TIME.STOP_SEQUENCE.lessThan(subq.STOP_SEQUENCE))
                                .and(subq.STOP_ID.eq(dest))
                                .and(subq.FEED_VERSION.eq(this.feedVersion)))
                        .and(STOP_TIME.TRIP_ID
                                .in(dsl.select(TRIP.TRIP_ID).from(TRIP)
                                        .where(TRIP.SERVICE_ID
                                                .in(dsl.select(SERVICE.SERVICE_ID).from(SERVICE)
                                                        .whereExists(dsl.selectFrom(SERVICE_EXCEPTION)
                                                                .where((SERVICE_EXCEPTION.DATE.eq(this.dateString()))
                                                                        .and(SERVICE.SERVICE_ID.eq(SERVICE_EXCEPTION.SERVICE_ID))
                                                                        .and(SERVICE_EXCEPTION.EXCEPTION_TYPE.eq(UByte.valueOf(1)))
                                                                        .and(SERVICE_EXCEPTION.FEED_VERSION.eq(this.feedVersion)))
                                                                .and(SERVICE.FEED_VERSION.eq(this.feedVersion)))
                                                        .or((DSL.val(UInteger.valueOf(this.dateString())).between(SERVICE.START_DATE.cast(SQLDataType.INTEGERUNSIGNED)).and(SERVICE.END_DATE.cast(SQLDataType.INTEGERUNSIGNED)))
                                                                .and(this.weekdays.get(this.date.get(Calendar.DAY_OF_WEEK)).eq((byte) 1))
                                                                .andNotExists(dsl.selectFrom(SERVICE_EXCEPTION)
                                                                        .where(SERVICE_EXCEPTION.DATE.eq(this.dateString()))
                                                                        .and(SERVICE.SERVICE_ID.eq(SERVICE_EXCEPTION.SERVICE_ID))
                                                                        .and(SERVICE_EXCEPTION.EXCEPTION_TYPE.eq(UByte.valueOf(2)))
                                                                        .and(SERVICE_EXCEPTION.FEED_VERSION.eq(this.feedVersion))))
                                                ))
                                        .and(TRIP.FEED_VERSION.eq(this.feedVersion))));

        if (this.route != null){
            return withoutRoute
                    .and(STOP_TIME.TRIP_ID.in(dsl.select(TRIP.TRIP_ID).from(TRIP)
                            .where(TRIP.ROUTE_ID.eq(this.route))
                            .and(TRIP.FEED_VERSION.eq(this.feedVersion)))).orderBy(STOP_TIME.DEPARTURE_TIME).fetchMaps();
        } else return withoutRoute.orderBy(STOP_TIME.DEPARTURE_TIME).fetchMaps();

    }
    //todo (a few lines above) .and(STOP_TIME.TRIP_ID.in(dsl.select(TRIP.TRIP_ID).from(TRIP)... this used to not have "from(TRIP)" in it.... the syntax error popped out

    enum Format{
        JSON, PLAIN
    }

    public void setRoute(String routeId){
        this.route = routeId;
    }

    public String dateString(){
        String yyyy = String.valueOf(this.date.get(Calendar.YEAR));
        String mm = String.format("%02d", this.date.get(Calendar.MONTH) + 1);
        String dd = String.format("%02d", this.date.get(Calendar.DATE));
        return yyyy+mm+dd;
    }



    public void setDate(int year, int month, int date) {
       this.date = new Calendar.Builder().setDate(year, month-1, date).build();
    }

//  /*  select * from stop_time where
//            stop_id = 2428685 and
//            exists
//--  where second comes after first
//            (select * from stop_time as st2 where
//                    stop_time.trip_id = st2.trip_id and
//                    stop_time.stop_sequence < st2.stop_sequence and
//                    st2.stop_id = 2428659)and
//    trip_id in
//--  occurs on particular day
//            (select trip_id from trip where
//                    service_id in
//                    (select service_id from service where exists
//                    -- is an additional service
//                    (select * from service_exception where
//                    service_exception.date = 20200103 and
//                    service.service_id = service_exception.service_id and
//                    service_exception.exception_type = 1) or
//                -- is a normal service that is not cancelled on that day
//                    20200103 between
//    cast(start_date as unsigned) and
//    cast(end_date as unsigned) and
//    saturday = 1
//    and not exists
//            (select * from service_exception where
//                    service_exception.date = 20200103 and
//                    service.service_id = service_exception.service_id and
//                    service_exception.exception_type = 2))
//            -- and route_id = 9507
//            );*/

    public String getTimetable() { //todo make sure this takes routes into account
        StopTime subq = STOP_TIME.as("subq");

        SelectConditionStep<StopTimeRecord> withoutRoute =
                dsl.selectFrom(STOP_TIME)
                    .where(STOP_TIME.STOP_ID.eq(origin))
                    .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion))
                    .andExists(dsl.selectFrom(subq)
                        .where(STOP_TIME.TRIP_ID.eq(subq.TRIP_ID))
                        .and(STOP_TIME.STOP_SEQUENCE.lessThan(subq.STOP_SEQUENCE))
                        .and(subq.STOP_ID.eq(dest))
                        .and(subq.FEED_VERSION.eq(this.feedVersion)))
                    .and(STOP_TIME.TRIP_ID
                        .in(dsl.select(TRIP.TRIP_ID).from(TRIP)
                            .where(TRIP.SERVICE_ID
                                .in(dsl.select(SERVICE.SERVICE_ID).from(SERVICE)
                                    .whereExists(dsl.selectFrom(SERVICE_EXCEPTION)
                                        .where((SERVICE_EXCEPTION.DATE.eq(this.dateString()))
                                        .and(SERVICE.SERVICE_ID.eq(SERVICE_EXCEPTION.SERVICE_ID))
                                        .and(SERVICE_EXCEPTION.EXCEPTION_TYPE.eq(UByte.valueOf(1)))
                                        .and(SERVICE_EXCEPTION.FEED_VERSION.eq(this.feedVersion)))
                                    .and(SERVICE.FEED_VERSION.eq(this.feedVersion)))
                                    .or((DSL.val(UInteger.valueOf(this.dateString())).between(SERVICE.START_DATE.cast(SQLDataType.INTEGERUNSIGNED)).and(SERVICE.END_DATE.cast(SQLDataType.INTEGERUNSIGNED)))
                                        .and(this.weekdays.get(this.date.get(Calendar.DAY_OF_WEEK)).eq((byte) 1))
                                        .andNotExists(dsl.selectFrom(SERVICE_EXCEPTION)
                                                .where(SERVICE_EXCEPTION.DATE.eq(this.dateString()))
                                                .and(SERVICE.SERVICE_ID.eq(SERVICE_EXCEPTION.SERVICE_ID))
                                                .and(SERVICE_EXCEPTION.EXCEPTION_TYPE.eq(UByte.valueOf(2)))
                                                .and(SERVICE_EXCEPTION.FEED_VERSION.eq(this.feedVersion))))
                                ))
                            .and(TRIP.FEED_VERSION.eq(this.feedVersion))));

        if (this.route != null){
            return withoutRoute
                    .and(STOP_TIME.TRIP_ID.in(dsl.select(TRIP.TRIP_ID)
                        .where(TRIP.ROUTE_ID.eq(this.route))
                        .and(TRIP.FEED_VERSION.eq(this.feedVersion)))).fetch().formatJSON(jf);
        } else return withoutRoute.fetch().formatJSON(jf);
    }

    public String getStops(String like) {
        return this.getStops(like, Format.PLAIN);
    }

    private String getStops(String like, Format f) {
        //todo make sure this takes routes into account
        Result withoutFormat = this.applyRouteStops(dsl.selectDistinct(STOP_TIME.STOP_ID, STOP.STOP_NAME).from(STOP_TIME.leftJoin(STOP).on(STOP.STOP_ID.eq(STOP_TIME.STOP_ID)))
                .where(STOP.STOP_NAME.like("%"+like+"%"))
                .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion))).fetch();
        if(f == Format.JSON){
            return withoutFormat.formatJSON(jf);
        } else return withoutFormat.toString();
    }

    private <R extends Record> SelectConditionStep applyRouteStops(SelectConditionStep<Record2<String, String>> where) {
        if(this.route != null){
            return where.and(STOP_TIME.TRIP_ID.in(this.dsl.select(TRIP.TRIP_ID).from(TRIP).where(TRIP.ROUTE_ID.eq(this.route))));//todo is this the same as an exists statement?
        }else return where;
    }

    public void setOrigin(String origin) { //todo what if it's not correct
        this.origin = origin;
    }

    public String getDestinations() {//todo make sure this takes routes into account
        return this.getDestinations("");
    }

    private String getDestinations(String like) {
        return this.getDestinations(like, Format.PLAIN);
    }

    private String getDestinations(String like, Format f) {
        StopTime subq = STOP_TIME.as("sq");
        Result withoutFormat = this.applyRoute(dsl.selectDistinct(STOP_TIME.STOP_ID, STOP.STOP_NAME).from(STOP_TIME.leftJoin(STOP).on(STOP.STOP_ID.eq(STOP_TIME.STOP_ID)))
                .whereExists(dsl.selectFrom(subq)
                        .where(subq.STOP_ID.eq(this.origin))
                        .and(STOP_TIME.STOP_SEQUENCE.greaterThan(subq.STOP_SEQUENCE))
                        .and(STOP_TIME.TRIP_ID.eq(subq.TRIP_ID))
                        .and(subq.FEED_VERSION.eq(this.feedVersion)))
                .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion)))
                .and(STOP.STOP_NAME.like("%"+like+"%")).orderBy(STOP.STOP_NAME.asc()).fetch(); //todo records may be truncated after 50 rows
        if (f == Format.JSON){
            return withoutFormat.formatJSON(jf);
        }else return withoutFormat.toString();
//        select distinct stop_id from stop_time where exists(select * from stop_time as st2 where stop_id = 2428685 and stop_time.stop_sequence > st2.stop_sequence and stop_time.trip_id = st2.trip_id);
    }


    //        trip_id in
//        (select distinct stop_time.trip_id from stop_time left join
//        trip left join
//        route on trip.route_id = route.route_id
//        on stop_time.trip_id = trip.trip_id
//        where
//                stop_id = 2428685 and
//                stop_sequence = 1 and
//        route.route_id = 9505)

    private SelectConditionStep applyRoute(SelectConditionStep<Record2<String, String>> withoutRoute) {
        if (route != null){
            return withoutRoute
                    .and(STOP_TIME.TRIP_ID.in(this.dsl.select(STOP_TIME.TRIP_ID).from(STOP_TIME.leftJoin(TRIP.leftJoin(ROUTE).on(TRIP.ROUTE_ID.eq(ROUTE.ROUTE_ID))).on(STOP_TIME.TRIP_ID.eq(TRIP.TRIP_ID)))
                        .where(STOP_TIME.STOP_ID.eq(this.origin))
                        .and(STOP_TIME.FEED_VERSION.eq(this.feedVersion))))
                    .and(ROUTE.ROUTE_ID.eq(this.route));
        }else return withoutRoute;
//

    }

    public Calendar getDate(){
        return (Calendar)date.clone();
    }


    public void setDestination(String dest) {
        this.dest = dest;
    }
}
