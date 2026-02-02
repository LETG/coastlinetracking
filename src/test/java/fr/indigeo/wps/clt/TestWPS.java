package fr.indigeo.wps.clt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.feature.FeatureCollection;
import org.junit.jupiter.api.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import fr.indigeo.wps.clt.utils.GeoJsonFileUtils;


public class TestWPS {

	private static final Logger LOGGER = LogManager.getLogger(TestWPS.class);

	private static final File dataDir = new File("data");
	private static final File refLineFile = new File(dataDir, "lineref_vougot.json");
	private static final File coastLinesFile = new File(dataDir, "tdc_vougot.json");

	@Test
	/**
	 * Test de la création des radiales et du calcul de distance entre les radiales et les traits de côte
	 */
	public void testDistance() {
		try {
			// Récupère les données des fichiers de tests
			FeatureCollection<SimpleFeatureType, SimpleFeature> refLineFc = getFeatureCollections(refLineFile);
			FeatureCollection<SimpleFeatureType, SimpleFeature> coastLines = getFeatureCollections(coastLinesFile);

			// Récupère les radiales en fonction du trait de référence et des paramètres
			FeatureCollection<SimpleFeatureType, SimpleFeature> drawRadialsFc = getRadialsTest(refLineFc, 50, 20,
					false);
			// Ecrit le résultat dans un fichier pour analyse
			getGeoJsonFile(drawRadialsFc, dataDir, "drawRadialsFc");
			
			FeatureCollection<SimpleFeatureType, SimpleFeature> distanceFc = CoastLinesTrackingWPS.getDistances(drawRadialsFc, coastLines);
			getGeoJsonFile(distanceFc, dataDir, "distancesFc");
			LOGGER.info("distanceFc.json est généré dans le dossier data de votre projet ! vous pouvez le visualiser maintenant.");
		} catch (FileNotFoundException e) {
			LOGGER.error("Fichiers introuvables", e);
			fail("Missing test data files: " + e.getMessage());
		} catch (IOException e) {
			LOGGER.error("Erreur entrées sorties", e);
			fail("Erreur reading or writing files : " + e.getMessage());
		}catch (Exception e) {
			fail("Exception: " + e.getMessage());
		} 
	}

	@Test
	public void testCreateRadials() {
		try{
			FeatureCollection<SimpleFeatureType, SimpleFeature> refLineFc = getFeatureCollections(refLineFile);
			FeatureCollection<SimpleFeatureType, SimpleFeature> drawRadialsFc = getRadialsTest(refLineFc, 50, 20,
					true);
			getGeoJsonFile(drawRadialsFc, dataDir, "drawRadialsFc");
		} catch (FileNotFoundException e) {
			LOGGER.error("Fichiers introuvables", e);
			fail("Missing test data files: " + e.getMessage());
		} catch (IOException e) {
			LOGGER.error("Erreur entrées sorties", e);
			fail("Erreur reading or writing files : " + e.getMessage());
		}catch (Exception e) {
			fail("Exception: " + e.getMessage());
		} 
	}

	@Test
	/**
	 * Test de tous les services du WPS CoastLinesTracking
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void testAllServices() {
		try {
			// draw radials Test
			FeatureCollection<SimpleFeatureType, SimpleFeature> refLineFc = getFeatureCollections(refLineFile);
			FeatureCollection<SimpleFeatureType, SimpleFeature> drawRadialsFc = getRadialsTest(refLineFc, 100, 50, false);
			getGeoJsonFile(drawRadialsFc, dataDir, "drawRadialsFc");
			LOGGER.info("drawRadialsFc.json est généré dans le dossier data de votre projet ! vous pouvez le visualiser maintenant.");

			// get distance Test
			FeatureCollection<SimpleFeatureType, SimpleFeature> coastLines = getFeatureCollections(coastLinesFile);
			FeatureCollection<SimpleFeatureType, SimpleFeature> distanceFc = getDistancesTest(drawRadialsFc, coastLines);
			getGeoJsonFile(distanceFc, dataDir, "distancesFc");
			LOGGER.info("distanceFc.json est généré dans le dossier data de votre projet ! vous pouvez le visualiser maintenant.");
			
			// get distance to csv Test
			String csvString = getDistancesToCSVTest(distanceFc);
			getCSVFile(csvString, dataDir, "distances.csv");
			LOGGER.info("distances.csv est généré dans le dossier data de votre projet ! vous pouvez le visualiser maintenant.");

			String jsonString = getDistancesToJsonTest(distanceFc);
			getCSVFile(jsonString, dataDir, "distances.json");
			LOGGER.info("jsonString est généré dans le dossier data de votre projet ! vous pouvez le visualiser maintenant.");

		} catch (FileNotFoundException e) {
			LOGGER.error("Fichiers introuvables", e);
			fail("Missing test data files: " + e.getMessage());
		} catch (IOException e) {
			LOGGER.error("Erreur entrées sorties", e);
			fail("Erreur reading or writing files : " + e.getMessage());
		}catch (Exception e) {
			fail("Exception: " + e.getMessage());
		} 
	}

	public static FeatureCollection<SimpleFeatureType, SimpleFeature> getRadialsTest(
			FeatureCollection<SimpleFeatureType, SimpleFeature> refLine, double length, double distance,
			boolean direction) {

		return CoastLinesTrackingWPS.drawRadial(refLine, length, distance, direction);
	}

	public static FeatureCollection<SimpleFeatureType, SimpleFeature> getDistancesTest(
			FeatureCollection<SimpleFeatureType, SimpleFeature> radials,
			FeatureCollection<SimpleFeatureType, SimpleFeature> coastLines) {

		return CoastLinesTrackingWPS.getDistances(radials, coastLines);
	}

	public static String getDistancesToCSVTest(FeatureCollection<SimpleFeatureType, SimpleFeature> distances) {

		return CoastLinesTrackingWPS.getDistancesToCSV(distances);
	}

	public static String getDistancesToJsonTest(FeatureCollection<SimpleFeatureType, SimpleFeature> distances) {

		return CoastLinesTrackingWPS.getDistancesToJson(distances);
	}

	public static FeatureCollection<SimpleFeatureType, SimpleFeature> getFeatureCollections(File refLineFile)
			throws FileNotFoundException, IOException {

		return GeoJsonFileUtils.geoJsonToFeatureCollection(refLineFile);
	}

	public static void getGeoJsonFile(FeatureCollection<SimpleFeatureType, SimpleFeature> data, File dir, String fileName) throws FileNotFoundException, IOException {
		GeoJsonFileUtils.featureCollectionToGeoJsonFile(data, dir, fileName);
	}

	public static void getCSVFile(String csvString, File dataDir, String fileName) {
		BufferedWriter bw = null;
		try {

			bw = new BufferedWriter(new FileWriter(new File(dataDir, fileName)));
			bw.write(csvString); // Replace with the string you are trying to write
		} catch (IOException e) {
			LOGGER.error("erreur entrées sorties", e);
		} finally {
			try {
				bw.close();
			} catch (IOException e) {
				LOGGER.error("erreur entrées sorties", e);
			}
		}

	}
}
