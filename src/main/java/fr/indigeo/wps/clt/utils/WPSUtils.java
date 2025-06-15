package fr.indigeo.wps.clt.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Class utilitaire pour les services WPS de trait de côte
 * 
 * @author Fatah M'SILI 
 * @author Pierre JEGO - pierre.jeg@jdev.fr pour IEUM LETG
 *
 */
public class WPSUtils {

	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	private static final Logger LOGGER = LogManager.getLogger(WPSUtils.class);

	/**
	 * @param track
	 * @param segmentLength
	 * 
	 * @return
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 */
	public static LinkedList<LineString> createSegments(Geometry track, double segmentLength)
			throws NoSuchAuthorityCodeException, FactoryException {

		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);

		LinkedList<Coordinate> coordinates = new LinkedList<Coordinate>();
		Collections.addAll(coordinates, track.getCoordinates());

		double accumulatedLength = 0;
		List<Coordinate> lastSegment = new ArrayList<Coordinate>();
		LinkedList<LineString> segments = new LinkedList<LineString>();
		Iterator<Coordinate> itCoordinates = coordinates.iterator();

		for (int i = 0; itCoordinates.hasNext() && i < coordinates.size() - 1; i++) {
			Coordinate c1 = coordinates.get(i);
			Coordinate c2 = coordinates.get(i + 1);

			lastSegment.add(c1);

			double length = Math.sqrt(Math.pow(c2.x - c1.x, 2) + Math.pow(c2.y - c1.y, 2));

			if (length + accumulatedLength >= segmentLength) {
				double offsetLength = segmentLength - accumulatedLength;
				double ratio = offsetLength / length;
				double dx = c2.x - c1.x;
				double dy = c2.y - c1.y;

				Coordinate segmentationPoint = new Coordinate(c1.x + (dx * ratio), c1.y + (dy * ratio));

				lastSegment.add(segmentationPoint); // segmentation point
				segments.add(geometryFactory.createLineString(lastSegment.toArray(new Coordinate[lastSegment.size()])));

				lastSegment = new ArrayList<Coordinate>(); // Resets the variable since a new segment will be built
				accumulatedLength = 0D;
				coordinates.add(i + 1, segmentationPoint);
			} else {
				accumulatedLength += length;
			}
		}

		lastSegment.add(coordinates.getLast()); // add the last seguement
		segments.add(geometryFactory.createLineString(lastSegment.toArray(new Coordinate[lastSegment.size()])));

		return segments;
	}

	/**
	 * Permet de calculer la ligne de référence à partir d'entités.
	 * 
	 * @param featureCollection
	 * @return
	 */
	public static LineString getReferenceLineFromFeature(
			FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		FeatureIterator<SimpleFeature> iterator = featureCollection.features();

		LineString result = null;
		// getLineString from Feature
		if (iterator.hasNext()) {
			SimpleFeature feature = iterator.next();
			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			if (geometry instanceof LineString) {
				result = geometryFactory.createLineString(geometry.getCoordinates());
			}
			iterator.close();
		}

		return result;
	}

	/**
	 * Fonction utilitaire petmettant de calculer la pente d'une géométrie de type
	 * ligne.
	 * 
	 * @param segment le segment
	 * @return {double}
	 */
	private static double getSlope(LineString segment) {
		// coefficient directeur entre le point de départ du segment et le point de fin
		// point de départ = x1,y1
		// point de fin = x2,y2
		// coefficient directeur = y2-y1/x2-x1
		return (segment.getEndPoint().getCoordinate().y - segment.getStartPoint().getCoordinate().y)
				/ (segment.getEndPoint().getCoordinate().x - segment.getStartPoint().getCoordinate().x);
	}

	/**
	 * Permet d'avoir le coefficient directeur de la radiale ou son inverse selon
	 * l'orientation.
	 * Cette méthode est obligatoire car le coef. directeur ne donne pas
	 * l'orientation mais si la pente est
	 * positive ou négative seulement.
	 * 
	 * @param line
	 * @param direction
	 * @return {double}
	 */
	private static double getRealDirection(LineString line, boolean direction) {
		double orientation = 0;
		double yStart = line.getStartPoint().getCoordinate().y;
		double yEnd = line.getEndPoint().getCoordinate().y;
		double xEnd = line.getEndPoint().getCoordinate().y;
		double xStart = line.getEndPoint().getCoordinate().x;
		if (direction == false) {
			orientation = 1;
			if (yStart < yEnd) {
				orientation = -1;
			}
			if (yEnd == yStart && xStart > xEnd) {
				orientation = -1;
			}
			if (yEnd == yStart && xStart < xEnd) {
				orientation = 1;
			}
		} else if (direction == true) {
			orientation = -1;
			if (yStart < yEnd) {
				orientation = 1;
			}
			if (yEnd == yStart && xStart > xEnd) {
				orientation = 1;
			}
			if (yEnd == yStart && xStart < xEnd) {
				orientation = -1;
			}
		}
		return orientation;
	}

	/**
	 * Permet de calculer le second point de la radiale. Le premier étant un point
	 * du segment sur la ligne de référence.
	 * 
	 * @param line        le segment de la ligne
	 * @param slope       la pente ou coefficient directeur
	 * @param distance    la distance entre les radiales
	 * @param sens        le sens de la radiale
	 * @param segmentType indique si on utilise le point de départ ou de fin du
	 *                    segment
	 * @return {Coordinate}
	 */
	private static Coordinate getNewPoint(LineString line, double slope, double distance, boolean sens,
			boolean segmentType) {
		// on identifie les coordonnées du point d'origine de la radiale
		double y = line.getStartPoint().getCoordinate().y;
		double x = line.getStartPoint().getCoordinate().x;
		// on calcule la pente ou le coefficient directeur
		double m = slope;

		// On défini un point sur la perpendiculaire selon la distance.
		// d : la distance
		// m : le coefficient directeur appelé aussi pente
		// x et y les coordonnée d'un point sur la droite (segment)
		m = -1 / m;
		// on va maintenant calculer le sens selon l'orientation du segment
		double orientation = getRealDirection(line, sens);
		// ensuite on défini le point de la radiale selon la distance et le point de
		// départ
		double x2 = x + orientation * (distance / Math.sqrt(1 + (m * m)));
		double y2 = y + orientation * m * (distance / Math.sqrt(1 + (m * m)));

		Coordinate newPoint = new Coordinate(x2, y2);

		LOGGER.debug("Calculate new radiale point");

		return newPoint;
	}

	/**
	 * Fonction de création d'un segment sur la ligne de référence.
	 * C'est la géométrie qui permettra le calcul de la radiale.
	 * 
	 * @param segment
	 * @param length
	 * @param sense
	 * @param segmentType
	 * @return {LineString}
	 */
	public static LineString createRadialSegment(LineString segment, double length,
			boolean sens,
			boolean segmentType) {
		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		// get point according to sens

		if (segment.getCoordinates().length > 2) {
			// si la géométrie est composé de plusieurs segments
			// alors on se base sur dernier segments de la géométrie
			// afin d'avoir le coef. directeur de ce dernier segment, et une radiale
			// correcte
			Coordinate[] coordinates = segment.getCoordinates();
			segment = geometryFactory
					.createLineString(new Coordinate[] { coordinates[0], coordinates[1] });
		}
		double slope = getSlope(segment);
		Coordinate p = getNewPoint(segment, slope, length, sens, segmentType);
		LineString radialSegment = geometryFactory.createLineString(
				new Coordinate[] { segment.getStartPoint().getCoordinate(), new Coordinate(p.x, p.y) });

		LOGGER.debug("Create radiale segment");

		return radialSegment;
	}


	/**
	 *  Retourne une liste de dates à partir d'une map de traits de côte
	 * 
	 * @param coastLineMap
	 * @return
	 */
	public static List<Date> getDatesFromCoastLinesMap(Map<Date, LineString> coastLineMap) {

		List<Date> dates = null;
		if (!coastLineMap.isEmpty()) {
			dates = new LinkedList<Date>();
			for (Map.Entry<Date, LineString> entry : coastLineMap.entrySet()) {
				dates.add(entry.getKey());
				LOGGER.debug("Date : " + entry.getKey());
			}
			LOGGER.debug("getDatesFromCoastLinesMap {} dates in list", dates.size() );
		}
		return dates;
	}

	/**
	 * 
	 * @param dates
	 * @param currentDate
	 * @return
	 */
	public static List<Date> getBeforDates(List<Date> dates, Date currentDate) {

		List<Date> datesBefore = new ArrayList<Date>();
		for (Date d : dates) {
			if (currentDate.compareTo(d) < 0) {
				datesBefore.add(d);
			}
		}

		return datesBefore;
	}

	/**
	 * Mesure la distance entre deux points via une ligne géométrique
	 * 
	 * @param startPoint
	 * @param endPoint
	 * @return
	 */
	public static double getDistance(Point startPoint, Point endPoint) {
		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		LineString line = geometryFactory.createLineString(
				new Coordinate[] { startPoint.getCoordinate(), endPoint.getCoordinate() });
		return line.getLength();
	}

	/**
	 * 
	 * @param radialsMap
	 * @param coastLinesMap
	 * @return
	 */
	public static Map<String, Map<Date, Point>> getIntersectedPoints(Map<String, LineString> radialsMap,
			Map<Date, LineString> coastLinesMap) {

		// create a map with a treeMap of intersected point
		Map<String, Map<Date, Point>> intersectedPoints = new HashMap<String, Map<Date, Point>>();

		// Pour chaque ligne de la radial
		for (Map.Entry<String, LineString> radial : radialsMap.entrySet()) {
			LineString radialGeom = radial.getValue();
			Map<Date, Point> intersectPoints = new HashMap<Date, Point>();
			// pour chaque traits de côte
			for (Map.Entry<Date, LineString> coastLine : coastLinesMap.entrySet()) {
				// Si la radial intersect le traît de côte
				LineString coastGeom = coastLine.getValue();
				Geometry intersectPoint = radialGeom.intersection(coastGeom);
				if (intersectPoint != null) {
					LOGGER.debug("getIntersectedPoints intersection entre la radial et un trait de cote");
					// Ajout le point d'intersection avec la date comme clé
					if (intersectPoint.getGeometryType() == Geometry.TYPENAME_POINT) {
						intersectPoints.put(coastLine.getKey(), (Point) intersectPoint);
					} else if (intersectPoint.getGeometryType() == Geometry.TYPENAME_MULTIPOINT) {
						// Get Mutipoint centroid
						intersectPoints.put(coastLine.getKey(), (Point) intersectPoint.getCentroid());
					} else {
						LOGGER.error("Intersection geometry type not handle " + intersectPoint.getGeometryType());
					}
				}
			}
			// ajout les points pour la radial
			intersectedPoints.put(radial.getKey(), new TreeMap<Date, Point>(intersectPoints));
		}

		LOGGER.debug("getIntersectedPoints  " + intersectedPoints.size() + " nb element in response");
		return intersectedPoints;
	}

	/**
	 * Créer une géométrie sur la radiale pour chaque trait de côte date intersecté
	 * par date
	 * 
	 * @param intersectedPoints
	 * @return
	 */
	public static Map<String, Map<Date[], LineString>> getComposedSegment(
			Map<String, Map<Date, Point>> intersectedPoints, Date dateRef) {

		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		Map<String, Map<Date[], LineString>> radialLinesByDates = new HashMap<String, Map<Date[], LineString>>();

		for (Map.Entry<String, Map<Date, Point>> intersectedPointOnradial : intersectedPoints.entrySet()) {

			LOGGER.debug("getComposedSegment traitement de radial :" + intersectedPointOnradial.getKey());
			if (intersectedPointOnradial.getValue().size() > 1) {
				Map<Date[], LineString> lines = new LinkedHashMap<Date[], LineString>();
				// dates et points d'intersections correspondants
				List<Date> DateList = new ArrayList<Date>(intersectedPointOnradial.getValue().keySet());
				LOGGER.debug("getComposedSegment keyList size " + DateList.size());

				// on ne va traiter que les radiales qui intersectent la date de référence
				// sinon il ne sera pas possible de comparer les TDC à la date de référence
				if (!DateList.contains(dateRef)) {
					continue;
				}
				for (int i = 0; i < DateList.size() - 1; i++) {
					Coordinate[] coordinates = new Coordinate[2];
					Date[] formToCoastLinesDate = new Date[2];

					Date firstDate = DateList.get(i);
					Date secondDate = DateList.get(i + 1);
					// point d'intersection pour la ligne à cette première date
					coordinates[0] = intersectedPointOnradial.getValue().get(firstDate).getCoordinate();
					// point d'intersection pour la ligne à cette seconde date
					coordinates[1] = intersectedPointOnradial.getValue().get(secondDate).getCoordinate();

					formToCoastLinesDate[0] = firstDate;
					formToCoastLinesDate[1] = secondDate;

					lines.put(formToCoastLinesDate, geometryFactory.createLineString(coordinates));
				}
				// {radialId: [lineA: {key: [Date1, Date2], value: LineString}}, lineB: {key:
				// [Date2, Date3], value: LineString}}]}
				radialLinesByDates.put(intersectedPointOnradial.getKey(), lines);
				LOGGER.debug("getComposedSegment  radiale n° " + intersectedPointOnradial.getKey());
			}
		}

		LOGGER.debug("getComposedSegment  " + radialLinesByDates.size() + " nb elements in response");
		return radialLinesByDates;
	}

	/**
	 * 
	 * @param distanceSeguments
	 * @param datesBefore
	 * @param radialeName
	 * @return
	 */
	public static double getCumulatedDistance(Map<String, Map<Date[], LineString>> distanceSeguments,
			List<Date> datesBefore, String radialeName) {

		LOGGER.debug("getCumulatedDistance for radial  " + radialeName);
		double cumulDist = 0;

		for (Map.Entry<String, Map<Date[], LineString>> radial : distanceSeguments.entrySet()) {
			for (Map.Entry<Date[], LineString> line : radial.getValue().entrySet()) {
				for (Date d : datesBefore) {
					double separateDistance = 0;
					if (line.getKey()[0].compareTo(d) == 0 && radial.getKey().equals(radialeName))
						if ((line.getValue().getStartPoint().getX() < line.getValue().getEndPoint().getX())
								&& (line.getValue().getStartPoint().getY() > line.getValue().getEndPoint().getY())) {

							separateDistance = -line.getValue().getLength();
						} else {
							separateDistance = line.getValue().getLength();
						}
					cumulDist = cumulDist + separateDistance;
				}
			}
		}
		LOGGER.debug("getCumulatedDistance for radial  " + radialeName + " equals " + cumulDist);
		return cumulDist;
	}

	/**
	 * 
	 * @param date1
	 * @param date2
	 * @return
	 */
	public static int getNbrDaysBetweenTwoDate(Date date1, Date date2) {

		long diff = date2.getTime() - date1.getTime();

		return (int) TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

	}

	/**
	 * 
	 * @param featureCollection
	 * @return
	 */
	public static TreeSet<Date> getDatesFromFeatures(
			FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {

		// use TreeSet to have unique dates and sorted
		TreeSet<Date> listOfDate = new TreeSet<Date>();
		LOGGER.debug("getDatesFromFeatures nb element in collection features {}", featureCollection.size());
		FeatureIterator<SimpleFeature> iterator = featureCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				Date fromDate = dateFormat.parse(feature.getProperty("fromDate").getValue().toString());
				Date toDate = dateFormat.parse(feature.getProperty("toDate").getValue().toString());
				listOfDate.add(fromDate);
				listOfDate.add(toDate);
		
				LOGGER.debug("FromDate : {} - toDate : {}", fromDate.toString(), toDate.toString());
			}

		} catch (Exception e) {
			LOGGER.error("Error while executing getDatesFromFeatures", e);
		} finally {
			iterator.close();
		}
		return listOfDate;
	}

	/**
	 * 
	 * @param featureCollection
	 * @return
	 */
	public static TreeSet<Integer> getRadialsNameFromFeatures(
			FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
		TreeSet<Integer> listOfRadialsName = new TreeSet<Integer>();
		FeatureIterator<SimpleFeature> iterator = featureCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				int radiale = Integer.valueOf(feature.getProperty("radiale").getValue().toString());
				listOfRadialsName.add(radiale);
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing getRadialsNameFromFeatures", e);
		} finally {
			iterator.close();
		}

		return listOfRadialsName;
	}

	/**
	 * 
	 * @param featureCollection
	 * @param type
	 * @param date
	 * @param radialeName
	 * @return
	 */
	public static double getDistanceByType(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
			int type, Date date, int radialeKey) {

		FeatureIterator<SimpleFeature> iterator = featureCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				int radiale = Integer.parseInt(feature.getProperty("radiale").getValue().toString());
				Date toDate = dateFormat.parse(feature.getProperty("toDate").getValue().toString());
				if (radialeKey == radiale && date.equals(toDate)) {
					// separate_dist
					if (type == 1)
						return (Double) feature.getProperty("separate_dist").getValue();
					// cumulate_dist
					if (type == 2)
						return (Double) feature.getProperty("cumulate_dist").getValue();
					// taux_recul
					if (type == 3)
						return (Double) feature.getProperty("taux_recul").getValue();
					if (type == 4)
						return (Double) feature.getProperty("fromStartDist").getValue();
					if (type == 5)
						return (Double) feature.getProperty("fromDateRefDist").getValue();
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing getDistanceByType", e);
		} finally {
			iterator.close();
		}
		return -1;
	}

	public static Map<String, LineString> mapRadials(FeatureCollection<SimpleFeatureType, SimpleFeature> input) {
		Map<String, LineString> mapRadials = new HashMap<String, LineString>();
		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		FeatureIterator<SimpleFeature> iterator = input.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				Geometry geometry = (Geometry) feature.getDefaultGeometry();
				if (geometry instanceof LineString) {
					LineString geomLineString = geometryFactory.createLineString(geometry.getCoordinates());
					mapRadials.put(feature.getProperty("name").getValue().toString(), geomLineString);
				} else {
					throw new Exception("Les geometries sont pas des LineString !");
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing mapRadials", e);
		} finally {
			iterator.close();
		}

		LOGGER.debug("mapRadials - {} elements in response", mapRadials.size());
		return mapRadials;
	}


    public static Map<Date, LineString> mapCoastlines(FeatureCollection<SimpleFeatureType, SimpleFeature> input) {
        // use Treemap to order by date
		Map<Date, LineString> mapCoastlines = new TreeMap<>();
		GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 2154);
		FeatureIterator<SimpleFeature> iterator = input.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				Geometry geometry = (Geometry) feature.getDefaultGeometry();
				if (geometry instanceof LineString) {
					LineString geomLineString = geometryFactory.createLineString(geometry.getCoordinates());
					// Depending on geoserver version date in json can be a String or Date
					if (feature.getProperty("creationdate").getValue() instanceof String) {
						String dateString = feature.getProperty("creationdate").getValue().toString();
						try {
							Date date = dateFormat.parse(dateString);
							LOGGER.debug("mapCoastlines Coastline date {}:", date);
							mapCoastlines.put(date, geomLineString);
						} catch (ParseException e) {
							LOGGER.error("Error parsing date from string: {}", dateString, e);
						}
					} else if (feature.getProperty("creationdate").getValue() instanceof Date) {
						LOGGER.debug("creationdate type {}:", feature.getProperty("creationdate").getValue().getClass());
						Date date = (Date) feature.getProperty("creationdate").getValue();
						LOGGER.debug("mapCoastlines Coastline date {}:", date);
						mapCoastlines.put(date, geomLineString);
					} else {
						LOGGER.error("Unsupported date type: " + feature.getProperty("creationdate").getValue().getClass());
					}
				} else {
					throw new Exception("Les geometries sont pas des LineString !");
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing mapCoastlines", e);
		} finally {
			iterator.close();
		}

		LOGGER.debug("mapCoastlines - {} elements in response", mapCoastlines.size());
		return mapCoastlines;
    }

}
