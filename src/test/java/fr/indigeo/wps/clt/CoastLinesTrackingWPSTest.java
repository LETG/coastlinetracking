package fr.indigeo.wps.clt;

import static org.junit.jupiter.api.Assertions.*;

import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.Date;

public class CoastLinesTrackingWPSTest {

    private GeometryFactory geometryFactory;
    private SimpleFeatureType lineType;
    private DefaultFeatureCollection radials;
    private DefaultFeatureCollection coastlines;

    @BeforeEach
    public void setUp() throws Exception {
    
        geometryFactory = new GeometryFactory();

        // Create a simple feature type for radials
        SimpleFeatureTypeBuilder radialTypeBuilder = new SimpleFeatureTypeBuilder();
        radialTypeBuilder.setName("Radial");
        radialTypeBuilder.add("geometry", LineString.class);
        radialTypeBuilder.add("type", String.class);
        radialTypeBuilder.add("name", String.class);
        lineType = radialTypeBuilder.buildFeatureType();

        // Create a simple feature type for coastlines
        SimpleFeatureTypeBuilder coastlineTypeBuilder = new SimpleFeatureTypeBuilder();
        coastlineTypeBuilder.setName("Coastline");
        coastlineTypeBuilder.add("geometry", LineString.class);
        coastlineTypeBuilder.add("creationdate", Date.class);
        
        SimpleFeatureType coastlineType = coastlineTypeBuilder.buildFeatureType();
        coastlines = new DefaultFeatureCollection(null, coastlineType);

        // Create some sample coastlines
        SimpleFeature coastline1 = SimpleFeatureBuilder.build(coastlineType,
                new Object[]{geometryFactory.createLineString(new Coordinate[]{
                        new Coordinate(0, 0), new Coordinate(1, 1)
                }), new Date()}, "coastline1");

        coastlines.add(coastline1);
       

        // Create some sample radials
        SimpleFeature radial1 = SimpleFeatureBuilder.build(lineType,
                new Object[]{geometryFactory.createLineString(new Coordinate[]{
                        new Coordinate(0, 0), new Coordinate(2, 2)
                }), "radiale", "radial1"}, "radial1");

        radials = new DefaultFeatureCollection(null, lineType);
        radials.add(radial1);
    }

    @Test
    public void testDrawRadialReturnsNotNull() {
        FeatureCollection<SimpleFeatureType, SimpleFeature> result = CoastLinesTrackingWPS.drawRadial(
                radials, 10.0, 5.0, true);
        assertNotNull(result);
    }

    @Test
    public void testGetDistancesReturnsNotNull() {
        FeatureCollection<SimpleFeatureType, SimpleFeature> result = CoastLinesTrackingWPS.getDistances(
                radials, coastlines);
        assertNotNull(result);
    }

    @Test
    public void testGetDistancesToCSVReturnsString() {
        FeatureCollection<SimpleFeatureType, SimpleFeature> distances = CoastLinesTrackingWPS.getDistances(radials, coastlines);
        String csv = CoastLinesTrackingWPS.getDistancesToCSV(distances);
        assertNotNull(csv);
        assertTrue(csv instanceof String);
    }

    @Test
    public void testGetDistancesToJsonReturnsString() {
        FeatureCollection<SimpleFeatureType, SimpleFeature> distances = CoastLinesTrackingWPS.getDistances(radials, coastlines);
        String json = CoastLinesTrackingWPS.getDistancesToJson(distances);
        assertNotNull(json);
        assertTrue(json instanceof String);
    }

    @Test
    public void testCoastLinesTrackingReturnsJsonString() {
        String json = CoastLinesTrackingWPS.coastLinesTracking(radials, coastlines);
        assertNotNull(json);
        assertTrue(json instanceof String);
    }
}