# Deparch (Long Island Rail Road Departure Timetables)

## HTTP methods : 2 GET methods

### /stops?name


Provides json output listing stops on the Long Island Rail Road (LIRR).

**Request parameters**:  
**name (optional)**: Filter LIRR stops by name.

**Returns:**  
- **stop_name**: The name of a stop on this route.
- **parent_station**: A unique identifier for this stop.

### /timetable?origin?year?month?date

Provides json output listing trip details for trains that stop at this station on a specified day.

**Request parameters:**
- **origin**: The origin station
- **year**: Desired year e.g. 2020
- **month**: Desired month e.g. 10 for October
- **date**: desired date e.g. 1 for the first day of a month.


**Returns:**
- **trip_id**: uniquely identifies a trip
- **origin**: the station where this trip began.
- **terminus**: the last stop of this trip.
- **departure_time** The time at which a train on this trip will depart the requested origin station (not necessarily the origin of the whole trip itself).
- **westbound** 1 or true if the train takes a westbound journey. 0 or false if the trip takes an eastbound journey.
