package fr.indigeo.wps.clt;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.indigeo.wps.clt.utils.WPSUtils;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.util.Comparator;

/**
 * @author Fatah M'SILI 
 * @author Pierre JEGO - pierre.jeg@jdev.fr pour IEUM LETG
 *
 */
@DescribeProcess(title = "Coastlines tracking project", description = "WPS for the tracking of coastlines")
public class CoastLinesTrackingWPS extends StaticMethodsProcessFactory<CoastLinesTrackingWPS>
		implements GeoServerProcess {

	private static final Logger LOGGER = LogManager.getLogger(CoastLinesTrackingWPS.class);

	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public CoastLinesTrackingWPS() {
		super(Text.text("WPS for the tracking of coastlines "), "coa", CoastLinesTrackingWPS.class);
	}

	/**
	 * @param referenceLine
	 * @param length
	 * @param distance
	 * @param direction
	 * @return
	 */
	@DescribeProcess(title = "Draw radial", description = "draw radial from reference Line")
	@DescribeResult(name = "resulFeatureCollection", description = "the result of drawing radials in reference Line")
	public static FeatureCollection<SimpleFeatureType, SimpleFeature> drawRadial(
			@DescribeParameter(name = "referenceLine", description = "the input referenceLine") final FeatureCollection<SimpleFeatureType, SimpleFeature> referenceLine,
			@DescribeParameter(name = "radialLength", description = "the length of radial in M") final double length,
			@DescribeParameter(name = "radialDistance", description = "the distance between radials in M") final double distance,
			@DescribeParameter(name = "radialDirection", description = "the direction of radial (true or false)") final boolean direction) {
		DefaultFeatureCollection resultFeatureCollection = null;

		LOGGER.info("DrawRadial with params - length : {} | distance :  {} | direction : {} ", length, distance, direction );

		try {
			SimpleFeatureTypeBuilder simpleFeatureTypeBuilder = new SimpleFeatureTypeBuilder();
			simpleFeatureTypeBuilder.setName("featureType");
			simpleFeatureTypeBuilder.add("geometry", LineString.class);
			simpleFeatureTypeBuilder.add("type", String.class);
			simpleFeatureTypeBuilder.add("name", String.class);

			if (referenceLine == null) {
				throw new Exception("Erreur de lecture de la ligne de référence vérifier les données en entrée");
			}
			LineString refLine = WPSUtils.getReferenceLineFromFeature(referenceLine);
			// create radials
			LinkedList<LineString> segements = WPSUtils.createSegments(refLine, distance);
			LinkedList<LineString> listRadiales = new LinkedList<LineString>();
			LineString radiale = null;

			LOGGER.debug("Create radials with {} elements", segements.size());

			int n = 0;
			for (LineString l : segements) {
				if (n == 0) {
					radiale = WPSUtils.createRadialSegment(l, length, direction, false);
					listRadiales.add(radiale);
				} else {
					radiale = WPSUtils.createRadialSegment(l, length, direction, true);
					listRadiales.add(radiale);
				}
				n++;
			}

			// init DefaultFeatureCollection
			SimpleFeatureBuilder simpleFeatureBuilder = new SimpleFeatureBuilder(
					simpleFeatureTypeBuilder.buildFeatureType());
			resultFeatureCollection = new DefaultFeatureCollection(null, simpleFeatureBuilder.getFeatureType());

			// add geometrie to defaultFeatures
			for (int i = 0; i < listRadiales.size(); i++) {
				int id = i + 1;
				simpleFeatureBuilder.add(listRadiales.get(i));
				simpleFeatureBuilder.add("radiale");
				simpleFeatureBuilder.add(id);
				resultFeatureCollection.add(simpleFeatureBuilder.buildFeature(Integer.toString(id)));
			}

		} catch (NoSuchAuthorityCodeException e) {
			LOGGER.error("Error while executing drawRadial", e);
		} catch (FactoryException e) {
			LOGGER.error("Error while executing drawRadial", e);
		} catch (Exception e) {
			LOGGER.error("Error while executing drawRadial", e);
		}

		return resultFeatureCollection;
	}

	/**
	 * 
	 * @param radials
	 * @param coastLines
	 * @return
	 */
	@DescribeProcess(title = "Calculate distances between coastlines", description = "calculate distances between coastlines using radials intersection.")
	@DescribeResult(name = "resultFeatureCollection", description = "the result of distance calculting.")
	public static FeatureCollection<SimpleFeatureType, SimpleFeature> getDistances(
			@DescribeParameter(name = "radials", description = "the result featureCollection from draw radials process") final FeatureCollection<SimpleFeatureType, SimpleFeature> radials,
			@DescribeParameter(name = "coastlines", description = "the input Coaslines") final FeatureCollection<SimpleFeatureType, SimpleFeature> coastLines) {

		DefaultFeatureCollection resultFeatureCollection = null;
		try {
			SimpleFeatureTypeBuilder simpleFeatureTypeBuilder = new SimpleFeatureTypeBuilder();
			simpleFeatureTypeBuilder.setName("featureType");
			simpleFeatureTypeBuilder.add("geometry", LineString.class);
			simpleFeatureTypeBuilder.add("radiale", Integer.class);
			simpleFeatureTypeBuilder.add("fromDate", String.class);
			simpleFeatureTypeBuilder.add("toDate", String.class);
			simpleFeatureTypeBuilder.add("cumulate_dist", Double.class);
			simpleFeatureTypeBuilder.add("taux_deux_dates", Double.class);
			simpleFeatureTypeBuilder.add("fromStartDist", Double.class);
			simpleFeatureTypeBuilder.add("fromDateRefDist", Double.class);
			simpleFeatureTypeBuilder.add("taux_recul", Double.class);
			simpleFeatureTypeBuilder.add("diff_prev_dist", Double.class);

			SimpleFeatureBuilder simpleFeatureBuilder = new SimpleFeatureBuilder(
					simpleFeatureTypeBuilder.buildFeatureType());

			// radiales calculées
			Map<String, LineString> radialsMap = WPSUtils.mapRadials(radials);
			// trait de côte calculé ou fourni en entrée
			Map<Date, LineString> coastLinesMap = WPSUtils.mapCoastlines(coastLines);
			//
			List<Date> dates = WPSUtils.getDatesFromCoastLinesMap(coastLinesMap);
			Optional<Date> minDateRef = dates.stream().min(Comparator.naturalOrder());
			// calcul de tous les points d'intersection entre geom radiales et geom trait de côte
			Map<String, Map<Date, Point>> intersectedPoints = WPSUtils.getIntersectedPoints(radialsMap, coastLinesMap);
			// 
			Map<String, Map<Date[], LineString>> RadialesDatesLines = WPSUtils.getComposedSegment(intersectedPoints, minDateRef.get());
			resultFeatureCollection = new DefaultFeatureCollection(null, simpleFeatureBuilder.getFeatureType());
			int id = 0;
			// on parcours les radiales une par une
			// et on traite les lignes créées à partir des dates qui intersectent les radiales
			for (Map.Entry<String, Map<Date[], LineString>> radialLines : RadialesDatesLines.entrySet()) {
				double accumulateDistance = 0;
				Point firstPoint = null;
				double distLineRefToFirstPoint = 0;
				int n = 0;
				Point RadialFirstPoint = null;
				// on va récupérer le premier point de la radiale parcourue
				for (Map.Entry<String, LineString> radial : radialsMap.entrySet()) {
					if (radialLines.getKey() == radial.getKey() && RadialFirstPoint == null) {
						LineString radialGeom = radial.getValue();
						RadialFirstPoint = radialGeom.getStartPoint();
					}
				}

				// Parcourir chaque ligne le long d'une radiale
				// Rappels :
				// - une ligne est créée le long d'une radiale entre des points d'intersection
				// - un point d'intersection est le croisement entre un trait de côte d'une année et la radiale
				Point dateRefPoint = null;
				double previousDist = 0;
				for (Map.Entry<Date[], LineString> dateLine : radialLines.getValue().entrySet()) {
					LOGGER.debug("Traitement de l'id : {}", id);
					double distFromStart = 0;
					// point de fin de la ligne
					Point endPoint = dateLine.getValue().getEndPoint();
					// premier segment date ref -> date ref + 1
					firstPoint = dateLine.getValue().getStartPoint();

					if (dateLine.getKey()[0] == minDateRef.get()) {
						dateRefPoint = firstPoint;
					}

					// distance à partir de la date de référence
					double distFromDateRef = WPSUtils.getDistance(dateRefPoint, endPoint);

					// distance depuis la radiale pour savoir si on est en positif ou négatif
					// par rapport à la date de référence
					double distLineRefToLastPoint = WPSUtils.getDistance(RadialFirstPoint, endPoint);
					// un point d'intersection correspond à une intersection entre
					// la radiale et un trait de côte à une date donnée

					// en partant de la première date utilisée comme date de référence la plus
					// ancienne
					// on va calculer la distance de chaque segment de date
					// line => LineString point[i] -> point[i+1]
					if (n == 0) {
						// distance radiale -> date de référence
						distLineRefToFirstPoint = WPSUtils.getDistance(RadialFirstPoint, firstPoint);
					}

					distFromStart = WPSUtils.getDistance(firstPoint, endPoint);

					// La distance cummulée est la distance totale de mouvement du trait parcourus
					// depuis la 1ere date de référence intersectée la plus ancienne jusqu'à
					// la date intersectée la plus récente.
					id++;
					accumulateDistance = accumulateDistance + distFromStart;
					int nbrJours = WPSUtils.getNbrDaysBetweenTwoDate(dateLine.getKey()[0], dateLine.getKey()[1]);

					// différence entre la dernière distance et la distance courante
					// permet d'avoir l'évolution
					double diffDistWithPrevious = distFromDateRef - previousDist;
					previousDist = distFromDateRef;

					// calculate sign
					if (distLineRefToLastPoint > distLineRefToFirstPoint) {
						// le recule du trait de côte est négatif car on perd de la distance
						// vis à vis de la ligne de référence et la date de référence
						distFromStart = -1 * distFromStart;
						distFromDateRef = -1 * distFromDateRef;
					}
					// taux d'évolution depuis la date de référence
					int nbrJoursFromDateRef = WPSUtils.getNbrDaysBetweenTwoDate(minDateRef.get(),
							dateLine.getKey()[1]);
					double nbYears = (double)nbrJoursFromDateRef / (double)365;
					double taux = distFromDateRef / nbYears;
					// Taux entre les deux années de la ligne
					double evolBetween = (distFromStart / nbrJours) * 365;

					// distance = Line -> point[i] -> point[i+1]
					simpleFeatureBuilder.add(dateLine.getValue());
					simpleFeatureBuilder.add(radialLines.getKey());
					simpleFeatureBuilder.add(dateFormat.format(dateLine.getKey()[0]));
					simpleFeatureBuilder.add(dateFormat.format(dateLine.getKey()[1]));
					simpleFeatureBuilder.add(accumulateDistance);
					simpleFeatureBuilder.add(evolBetween);
					simpleFeatureBuilder.add(distFromStart);
					simpleFeatureBuilder.add(distFromDateRef);
					simpleFeatureBuilder.add(taux);
					simpleFeatureBuilder.add(diffDistWithPrevious);
					resultFeatureCollection.add(simpleFeatureBuilder.buildFeature(Integer.toString(id)));
					n++;
					LOGGER.debug("Distance information : radial - " + radialLines.getKey() + " Date - "
							+ dateLine.getKey()[0] + " Date - " + dateLine.getKey()[1]);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing getDistances", e);
		}

		return resultFeatureCollection;
	}

	@DescribeProcess(title = "distancesToCSV", description = "parse a feautureCollection of distances to csv String.")
	@DescribeResult(name = "csvString", description = "the result of parsing distances.")
	public static String getDistancesToCSV(
			@DescribeParameter(name = "distancesFeatureCollection", description = "the result distance feature collection") final FeatureCollection<SimpleFeatureType, SimpleFeature> distances) {

		TreeSet<Date> dates = WPSUtils.getDatesFromFeatures(distances);

		String sep = "|";
		String eol = "\n";
		String headers = "";
		String subHeaders = "";
		String rad = "rad";
		String csv = "";

		if(dates.isEmpty()) {
			LOGGER.warn("No dates found in distances feature collection.");
			return "No data available.";
		}
		List<String> dataList = new ArrayList<String>();

		for (Date d : dates) {
			headers = headers + dateFormat.format(d) + "|";
		}

		LOGGER.debug("distancesToCSV - headers " + headers);

		headers = headers.substring(0, headers.length() - 1) + eol;

		for (int i = 1; i < dates.size(); i++) {
			subHeaders = subHeaders + "separe;cumule;taux,fromStartDist,fromDateRefDist" + sep;
		}

		subHeaders = rad + sep + subHeaders.substring(0, subHeaders.length() - 1) + eol;
		dataList.add(headers);
		dataList.add(subHeaders);

		for (Integer radiale : WPSUtils.getRadialsNameFromFeatures(distances)) {
			String data = radiale + sep;
			for (Date currentDate : dates){

				double cumulateDist = WPSUtils.getDistanceByType(distances, 1, currentDate, radiale);
				double taux = WPSUtils.getDistanceByType(distances, 3, currentDate, radiale);
				double distFromStart = WPSUtils.getDistanceByType(distances, 4, currentDate, radiale);
				double fromDateRefDist = WPSUtils.getDistanceByType(distances, 5, currentDate, radiale);

				if (cumulateDist != -1)
					data = data + cumulateDist + ";";
				else
					data = data + "none" + ";";

				if (taux != -1)
					data = data + taux;
				else
					data = data + "none";

				if (distFromStart != -1)
					data = data + distFromStart;
				else
					data = data + "none";
				
				if (fromDateRefDist != -1)
					data = data + fromDateRefDist;
				else
					data = data + "none";					

				data = data + sep;
			}

			data = data.substring(0, data.length() - 1) + eol;
			dataList.add(data);

		}

		for (String line : dataList) {
			csv = csv + line;
		}

		return csv;
	}

	@DescribeProcess(title = "distancesToJson", description = "parse a feautureCollection of distances to json object.")
	@DescribeResult(name = "JsonString", description = "the result of parsing distances.")
	public static String getDistancesToJson(
			@DescribeParameter(name = "distancesFeatureCollection", description = "the result distance feature collection") final FeatureCollection<SimpleFeatureType, SimpleFeature> distances) {

		TreeSet<Date> TDCdates = WPSUtils.getDatesFromFeatures(distances);

		JSONObject result = new JSONObject();
		JSONArray resultsArray = new JSONArray();

		// get all tdc result
		for (Date TDCdate : TDCdates) {

			JSONObject tdc = new JSONObject();
			JSONArray tdcValues = new JSONArray();
			tdc.put("date", dateFormat.format(TDCdate));

			FeatureIterator<SimpleFeature> iterator = distances.features();
			try {
				while (iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					Date toDate = dateFormat.parse(feature.getProperty("toDate").getValue().toString());

					// if wanted tdc -> store value to json
					if (TDCdate.equals(toDate)) {
						JSONObject tdcdata = new JSONObject();
						tdcdata.put("radiale", (Integer) feature.getProperty("radiale").getValue());
						// cumulate_dist
						tdcdata.put("cumulateDist", (Double) feature.getProperty("cumulate_dist").getValue());
						// taux_recul
						tdcdata.put("tauxRecul", (Double) feature.getProperty("taux_recul").getValue());
						tdcdata.put("fromStartDist", (Double) feature.getProperty("fromStartDist").getValue());
						tdcdata.put("fromDateRefDist", (Double) feature.getProperty("fromDateRefDist").getValue());
						tdcValues.add(tdcdata);
					}
				}
				tdc.put("data", tdcValues);
			} catch (ClassCastException e) {
				LOGGER.error("Error while casting distance to double", e);
			} catch (ParseException e) {
				LOGGER.error("Error while parsing date", e);
			} finally {
				iterator.close();
			}
			resultsArray.add(tdc);
		}
		result.put("result", resultsArray);
		return result.toString();
	}

	@DescribeProcess(title = "coastLinesTracking", description = "coastLinesTracking WPS")
	@DescribeResult(name = "jsonString", description = "the result of coastLinesTracking WPS")
	public static String coastLinesTracking(
			@DescribeParameter(name = "radiales", description = "the calculated radial") final FeatureCollection<SimpleFeatureType, SimpleFeature> radiales,
			@DescribeParameter(name = "coastlines", description = "the input Coastlines") final FeatureCollection<SimpleFeatureType, SimpleFeature> coastlines) {

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Input coastline is empty - {} ", coastlines.isEmpty());
			LOGGER.debug("coastlines class : {}", coastlines.getClass());
			LOGGER.debug("radiales class : {}", radiales.getClass());
			LOGGER.debug("Radial information : ");
			FeatureIterator<SimpleFeature> iteratorfc1 = radiales.features();
			try {
				while (iteratorfc1.hasNext()) {
					SimpleFeature feature = iteratorfc1.next();
					LOGGER.debug("Radial feature id = " + feature.getID());
					for (Property property : feature.getProperties()) {
						LOGGER.debug(property.getName() + " = " + property.getValue());
					}
				}
			} finally {
				iteratorfc1.close();
			}
		}

		FeatureCollection<SimpleFeatureType, SimpleFeature> fc2 = getDistances(radiales, coastlines);

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(coastlines.size() + " coastlines features to process");
			LOGGER.debug("Distance information : ");
			FeatureIterator<SimpleFeature> iteratorfc2 = fc2.features();
			try {
				while (iteratorfc2.hasNext()) {
					SimpleFeature feature = iteratorfc2.next();
					LOGGER.debug("Distance feature id = " + feature.getID());
					for (Property property : feature.getProperties()) {
						LOGGER.debug(property.getName() + " = " + property.getValue());
					}
				}
			} finally {
				iteratorfc2.close();
			}
		}

		String json = getDistancesToJson(fc2);

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Json information : ");
			LOGGER.debug("Json result : " + json);
		}

		return json;
	}

}
