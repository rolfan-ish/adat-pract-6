package io.gitlab.rolfan;

import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Main {

    static final String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";
    static final String USER = "admin";
    static final String PASSWORD = "";
    static final String DRIVER = "org.exist.xmldb.DatabaseImpl";

    public static void main(String[] args) {
        Collection collection = null;
        XMLResource resource = null;
        try {
            final var cl = Class.forName(DRIVER); // Tomamos el driver
            final var database = (Database) cl.getConstructor().newInstance(); // Creamos la bbdd
            database.setProperty("create-database", "true"); // Le decimos que si la bbdd no existe, la cree
            DatabaseManager.registerDatabase(database); // registramos la bbdd


            collection = getOrCreateCollection("/db/GIMNASIO");

            final var query = """
                    for $socio in doc("/db/socios_gim.xml")/SOCIOS_GIM/fila_socios
                    let $codigo := $socio/COD
                    let $nombre := $socio/NOMBRE
                    let $actividades := doc("/db/uso_gimnasio.xml")/USO_GIMNASIO/fila_uso[CODSOCIO = $codigo/text()]
                    return
                    <datos>
                        <COD>{$codigo}</COD>
                        <NOMBRESOCIO>{$nombre}</NOMBRESOCIO>
                        {
                            for $actividad in $actividades
                            let $codAct := $actividad/CODACTIV
                            let $nombreActividad := doc("/db/actividades_gim.xml")/ACTIVIDADES_GIM/fila_actividades[@cod = $codAct]/NOMBRE
                            let $tipo := doc("/db/ACTIVIDADES_GIM.xml")/ACTIVIDADES_GIM/fila_actividades[@cod = $codAct]/@tipo
                            let $horas := xs:int($actividad/HORAFINAL) - xs:int($actividad/HORAINICIO)
                            let $cuotaAdicional := $horas * 10
                            return
                            <actividad>
                                <CODACTIV>{$codAct}</CODACTIV>
                                <NOMBREACTIVIDAD>{$nombreActividad}</NOMBREACTIVIDAD>
                                <horas>{$horas}</horas>
                                <tipoact>{$tipo}</tipoact>
                                <cuota_adicional>{$cuotaAdicional}</cuota_adicional>
                            </actividad>
                        }
                    </datos>
                    """;

            final var queryService = (XPathQueryService) collection.getService("XPathQueryService", "1.0");
            resource = (XMLResource) collection.createResource("GIMNASIO_DATA.xml", "XMLResource");
            final var resultXml = new StringBuilder("<datosCollection>");
            final var iterator = queryService.query(query).getIterator();
            while (iterator.hasMoreResources()) {
                resultXml.append(iterator.nextResource().getContent().toString());
            }
            resultXml.append("</datosCollection>");
            resource.setContent(resultXml.toString());
            collection.storeResource(resource);

            generateSummaryXML(collection);
        } catch (XMLDBException | ClassNotFoundException | InvocationTargetException | InstantiationException |
                 IllegalAccessException | NoSuchMethodException | IOException e) {
            e.printStackTrace();
        } finally {
            // Cerramos todos los recursos por si acaso

            if (collection != null) {
                try {
                    collection.close();
                } catch (XMLDBException e) {
                    e.printStackTrace();
                }
            }
            if (resource != null) {
                try {
                    ((EXistResource)resource).close();
                } catch (XMLDBException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /// Accede a una colección o la crea
    /// @param collection Nombre de la colección
    /// @return Colección en el parametro global URI
    static Collection getOrCreateCollection(final String collection) throws XMLDBException {
        var col = DatabaseManager.getCollection(URI + collection, USER, PASSWORD);
        if (col != null) return col;

        final var root = DatabaseManager.getCollection(URI + "/db", USER, PASSWORD);
        final var service = (CollectionManagementService) root.getService("CollectionManagementService", "1,0");
        return service.createCollection(collection);
    }

    /// Genera el resumen (segundo punto)
    /// @param col Colección de donde leer los datos
    private static void generateSummaryXML(final Collection col) throws XMLDBException, IOException {
        final var service = (XPathQueryService) col.getService("XPathQueryService", "1.0");

        final var query = """
            for $socio in doc("/db/GIMNASIO/GIMNASIO_DATA.xml")/datosCollection/datos
            let $cuotaFija := doc("/db/socios_gim.xml")/SOCIOS_GIM/fila_socios[COD = $socio/COD]/CUOTA_FIJA/text()
            let $sumaCuotaAdic := sum($socio/actividad/cuota_adicional)
            let $cuotaTotal := xs:decimal($cuotaFija) + $sumaCuotaAdic
            return
            <datos>
                <COD>{$socio/COD/text()}</COD>
                <NOMBRESOCIO>{$socio/NOMBRESOCIO/text()}</NOMBRESOCIO>
                <CUOTA_FIJA>{$cuotaFija}</CUOTA_FIJA>
                <suma_cuota_adic>{$sumaCuotaAdic}</suma_cuota_adic>
                <cuota_total>{$cuotaTotal}</cuota_total>
            </datos>
            """;

        final var resultSet = service.query(query);

        final var summaryXml = new StringBuilder("<resumen>");
        final var iterator = resultSet.getIterator();
        while (iterator.hasMoreResources()) {
            summaryXml.append(iterator.nextResource().getContent().toString());
        }
        summaryXml.append("</resumen>");

        final var outputFile = new File("SummaryOutput.xml");
        try (final var writer = new FileWriter(outputFile)) {
            writer.write(summaryXml.toString());
        }

        System.out.println("Resumen generado en: " + outputFile.getAbsolutePath());
    }

}