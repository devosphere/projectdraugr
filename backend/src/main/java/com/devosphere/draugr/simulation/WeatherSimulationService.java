package com.devosphere.draugr.simulation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class WeatherSimulationService {
    private final JdbcTemplate jdbc;
    public WeatherSimulationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT world_id,seed FROM world_genesis",rs->{while(rs.next()){UUID world=rs.getObject(1,UUID.class);long seed=rs.getLong(2);long day=now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();int phase=Math.floorMod((int)(seed+day),4);String kind=switch(phase){case 0->"CLEAR";case 1->"OVERCAST";case 2->"RAIN";default->"STORM";};int hour=now.atZone(ZoneOffset.UTC).getHour();double base=12+8*Math.sin((hour-6)*Math.PI/12);int intensity="CLEAR".equals(kind)?10:"OVERCAST".equals(kind)?35:"RAIN".equals(kind)?65:90;int wind="STORM".equals(kind)?55:"RAIN".equals(kind)?24:12;jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) VALUES (?,?,?,?,?,?) ON CONFLICT (world_id) DO UPDATE SET weather_kind=EXCLUDED.weather_kind,intensity=EXCLUDED.intensity,ambient_temperature_c=EXCLUDED.ambient_temperature_c,wind_speed_kph=EXCLUDED.wind_speed_kph,observed_at=EXCLUDED.observed_at",world,kind,intensity,base,wind,now);}return null;});
    }
}
