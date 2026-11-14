package name.abuchen.portfolio.morningstar;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;

/**
 * Prove del classificatore con risposte di Morningstar costruite a mano,
 * con la stessa struttura letta dallo script pp-portfolio-classifier.
 */
@SuppressWarnings("nls")
public class MorningstarClassificatoreTest
{
    private static final String ETF_AZIONARIO = "IE00AZIONARI";
    private static final String ETF_OBBLIGAZIONARIO = "IE00OBBLIGAZ";
    private static final String AZIONE = "US0000AZIONE";

    private static final String ETF_SNAPSHOT = """
                    [{"Type":"Fund","Name":"ETF Azionario Mondo"}]
                    """;

    private static final String ETF_IT = """
                    [{"Type":"Fund","CategoryBroadAssetClass":{"Name":"Equity"},
                      "Portfolios":[{
                        "AssetAllocations":[
                          {"Type":"MorningStarDefault","SalePosition":"L","BreakdownValues":[{"Type":"1","Value":50}]},
                          {"Type":"MorningStarDefault","SalePosition":"N","BreakdownValues":[
                            {"Type":"1","Value":98.0},{"Type":"7","Value":2.0},{"Type":"99","Value":0}]}],
                        "StyleBoxBreakdown":[{"SalePosition":"N","BreakdownValues":[
                            {"Type":"1","Value":40},{"Type":"2","Value":35},{"Type":"3","Value":25}]}],
                        "GlobalStockSectorBreakdown":[{"SalePosition":"N","BreakdownValues":[
                            {"Type":"311","Value":30},{"Type":"103","Value":70}]}],
                        "RegionalExposure":[{"SalePosition":"N","BreakdownValues":[
                            {"Type":"1","Value":60},{"Type":"14","Value":90},{"Type":"5","Value":40}]}],
                        "CountryExposure":[{"Type":"Equity","SalePosition":"N","BreakdownValues":[
                            {"Type":"USA","Value":60},{"Type":"DEU","Value":40}]}]
                      }]}]
                    """;

    private static final String ETF_HOLDINGS = """
                    [{"Portfolios":[{"PortfolioHoldings":[
                        {"ISIN":"US0378331005","SecurityName":"Apple Inc","Weighting":5.5},
                        {"SecurityName":"Futures senza ISIN","Weighting":1.0},
                        {"ISIN":"US5949181045","SecurityName":"Microsoft Corp, Class A","Weighting":4.5}]}]}]
                    """;

    private static final String BOND_SNAPSHOT = """
                    [{"Type":"Fund","Name":"ETF Monetario"}]
                    """;

    private static final String BOND_IT = """
                    [{"Type":"Fund","CategoryBroadAssetClass":{"Name":"Capital Preservation"},
                      "Portfolios":[{
                        "AssetAllocations":[{"Type":"MorningStarDefault","SalePosition":"N","BreakdownValues":[
                            {"Type":"3","Value":80},{"Type":"7","Value":20}]}],
                        "GlobalBondSectorBreakdownLevel1":[{"SalePosition":"N","BreakdownValues":[
                            {"Type":"10","Value":100}]}],
                        "CountryExposure":[{"Type":"Bond","SalePosition":"N","BreakdownValues":[
                            {"Type":"FRA","Value":50},{"Type":"ITA","Value":50}]}]
                      }]}]
                    """;

    private static final String BOND_ETF = """
                    [{"Portfolios":[{"BondStyleBoxBreakdown":[{"SalePosition":"N","BreakdownValues":[
                            {"Type":"1","Value":100}]}]}]}]
                    """;

    private static final String AZIONE_SNAPSHOT = """
                    [{"Type":"Stock","Name":"Azienda Spa","Country":"USA",
                      "Sector":{"SectorCode":"311"},"Dettagli":{"InvestmentStyle":"3"}}]
                    """;

    private final List<String> log = new ArrayList<>();

    private MorningstarDati.Fonte fonte()
    {
        Map<String, String> risposte = new HashMap<>();
        risposte.put(ETF_AZIONARIO + "/snapshot", ETF_SNAPSHOT);
        risposte.put(ETF_AZIONARIO + "/ITsnapshot", ETF_IT);
        risposte.put(ETF_AZIONARIO + "/Allholdings", ETF_HOLDINGS);
        risposte.put(ETF_OBBLIGAZIONARIO + "/snapshot", BOND_SNAPSHOT);
        risposte.put(ETF_OBBLIGAZIONARIO + "/ITsnapshot", BOND_IT);
        risposte.put(ETF_OBBLIGAZIONARIO + "/ETFsnapshot", BOND_ETF);
        risposte.put(AZIONE + "/snapshot", AZIONE_SNAPSHOT);

        return (isin, viewId) -> {
            var json = risposte.get(isin + "/" + viewId);
            return json != null ? JsonParser.parseString(json) : (JsonElement) null;
        };
    }

    @Test
    public void testFondoAzionario() throws Exception
    {
        var g = MorningstarDati.classifica(ETF_AZIONARIO, fonte(), MorningstarDati.Opzioni.di(false, 100), log::add);

        assertThat(g.get("Asset Type").get("Stocks"), closeTo(98, 0.0001));
        assertThat(g.get("Asset Type").get("Cash"), closeTo(2, 0.0001));
        assertThat(g.get("Asset Type").size(), is(2));

        // stile e settore pesati con la quota azionaria netta (98%)
        assertThat(g.get("Stock Style").get("Large Value"), closeTo(39.2, 0.0001));
        assertThat(g.get("Stock Sector").get("Technology"), closeTo(29.4, 0.0001));

        // le regioni 1 (USA) e 5 (Eurozona); il totale 14 non è una categoria
        assertThat(g.get("Region").get("North America"), closeTo(58.8, 0.0001));
        assertThat(g.get("Region").get("Europe Developed"), closeTo(39.2, 0.0001));
        assertThat(g.get("Region").size(), is(2));

        assertThat(g.get("Country").size(), is(2));

        // solo le posizioni con ISIN; la virgola diventa trattino
        assertThat(g.get("Holding").get("Apple Inc"), closeTo(5.5, 0.0001));
        assertThat(g.get("Holding").get("Microsoft Corp- Class A"), closeTo(4.5, 0.0001));
        assertThat(g.get("Holding").size(), is(2));

        assertThat(g.get("Bond Style").isEmpty(), is(true));
    }

    @Test
    public void testCapitalPreservationComeObbligazionario() throws Exception
    {
        var g = MorningstarDati.classifica(ETF_OBBLIGAZIONARIO, fonte(), MorningstarDati.Opzioni.di(false, 100),
                        log::add);

        assertThat(g, notNullValue());
        assertThat(g.get("Asset Type").get("Bonds"), closeTo(80, 0.0001));
        assertThat(g.get("Bond Sector").get("Government"), closeTo(80, 0.0001));
        assertThat(g.get("Bond Style").get("High Quality - Short Term"), closeTo(80, 0.0001));

        // obbligazioni separate nei paesi e nelle regioni
        var paesi = g.get("Country");
        assertThat(paesi.size(), is(2));
        paesi.keySet().forEach(k -> assertThat(k.endsWith(" (Bonds)"), is(true)));
        assertThat(paesi.values().stream().mapToDouble(Double::doubleValue).sum(), closeTo(80, 0.0001));

        var regioni = g.get("Region");
        regioni.keySet().forEach(k -> assertThat(k.endsWith(" (Bonds)"), is(true)));

        assertThat(g.get("Stock Style").isEmpty(), is(true));
    }

    @Test
    public void testAzioneSoloConOpzione() throws Exception
    {
        assertThat(MorningstarDati.classifica(AZIONE, fonte(), MorningstarDati.Opzioni.di(false, 100), log::add),
                        nullValue());

        var g = MorningstarDati.classifica(AZIONE, fonte(), MorningstarDati.Opzioni.di(true, 100), log::add);
        assertThat(g.get("Asset Type").get("Stocks"), closeTo(100, 0.0001));
        assertThat(g.get("Stock Sector").get("Technology"), closeTo(100, 0.0001));
        assertThat(g.get("Stock Style").get("Large Growth"), closeTo(100, 0.0001));
        assertThat(g.get("Holding").get("Azienda Spa"), closeTo(100, 0.0001));
    }

    @Test
    public void testNumeroPosizioni() throws Exception
    {
        var g = MorningstarDati.classifica(ETF_AZIONARIO, fonte(), MorningstarDati.Opzioni.di(false, 1), log::add);
        assertThat(g.get("Holding").size(), is(1));
    }

    @Test
    public void testApplicaAlFile()
    {
        var client = new Client();
        var etf = titolo(client, "ETF Azionario", ETF_AZIONARIO, null);
        var bond = titolo(client, "ETF Monetario", ETF_OBBLIGAZIONARIO, null);
        titolo(client, "Saltato", ETF_AZIONARIO, "nota #PPC:SKIP");
        titolo(client, "Senza ISIN", null, null);

        var opzioni = MorningstarDati.Opzioni.di(false, 100);
        var risultati = MorningstarClassificatore.leggi(client, fonte(), opzioni, log::add, () -> false);
        assertThat(risultati.size(), is(2));

        MorningstarClassificatore.applica(client, risultati, log::add);

        var assetType = tassonomia(client, "Asset Type");
        assertThat(peso(assetType, "Stocks", etf), is(9800));
        assertThat(peso(assetType, "Cash", etf), is(200));
        assertThat(peso(assetType, "Bonds", bond), is(8000));
        assertThat(peso(assetType, "Cash", bond), is(2000));

        assertThat(peso(tassonomia(client, "Holding"), "Apple Inc", etf), is(550));

        // una seconda esecuzione non duplica nulla
        risultati = MorningstarClassificatore.leggi(client, fonte(), opzioni, log::add, () -> false);
        MorningstarClassificatore.applica(client, risultati, log::add);

        assertThat(client.getTaxonomies().stream().filter(t -> t.getName().equals("Asset Type")).count(), is(1L));
        assetType = tassonomia(client, "Asset Type");
        assertThat(assetType.getRoot().getChildren().size(), is(3));
        assertThat(peso(assetType, "Stocks", etf), is(9800));
        assertThat(assetType.getClassifications(etf).size(), is(2));
    }

    @Test
    public void testRestaLaClassificazioneManuale()
    {
        var client = new Client();
        var etf = titolo(client, "ETF Azionario", ETF_AZIONARIO, null);

        // Bond Style scritto a mano: Morningstar non dà dati per un fondo
        // azionario, quindi deve restare
        var taxonomy = new Taxonomy("Bond Style");
        taxonomy.setRootNode(new Classification("root", "Bond Style"));
        var manuale = new Classification(taxonomy.getRoot(), "x", "Manuale");
        taxonomy.getRoot().addChild(manuale);
        manuale.addAssignment(new Classification.Assignment(etf, 5000));
        client.addTaxonomy(taxonomy);

        var risultati = MorningstarClassificatore.leggi(client, fonte(), MorningstarDati.Opzioni.di(false, 100),
                        log::add, () -> false);
        MorningstarClassificatore.applica(client, risultati, log::add);

        assertThat(peso(taxonomy, "Manuale", etf), is(5000));
    }

    @Test
    public void testMappe()
    {
        var mappe = MorningstarMappe.get();
        assertThat(mappe.paesi().get("ITA"), notNullValue());
        assertThat(mappe.colori().size(), is(133));
        assertThat(MorningstarClassificatore.pulisci("Procter &amp; Gamble"), is("Procter & Gamble"));
    }

    private static Security titolo(Client client, String nome, String isin, String nota)
    {
        var s = new Security(nome, "EUR");
        s.setIsin(isin);
        s.setNote(nota);
        client.addSecurity(s);
        return s;
    }

    private static Taxonomy tassonomia(Client client, String nome)
    {
        return client.getTaxonomies().stream().filter(t -> t.getName().equals(nome)).findFirst().orElseThrow();
    }

    private static int peso(Taxonomy taxonomy, String categoria, Security security)
    {
        return taxonomy.getRoot().getChildren().stream().filter(c -> c.getName().equals(categoria))
                        .flatMap(c -> c.getAssignments().stream())
                        .filter(a -> a.getInvestmentVehicle() == security)
                        .mapToInt(Classification.Assignment::getWeight).sum();
    }
}
