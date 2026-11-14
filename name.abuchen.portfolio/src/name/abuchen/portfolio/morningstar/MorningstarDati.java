package name.abuchen.portfolio.morningstar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Legge da Morningstar la composizione di un titolo e la ripartisce nelle
 * tassonomie. Riproduce la logica dello script pp-portfolio-classifier con le
 * modifiche di Giacomo: anche i fondi obbligazionari e "Capital Preservation"
 * (trattati come obbligazionari), obbligazioni separate nei paesi e nelle
 * regioni ("Francia (Bonds)").
 */
@SuppressWarnings("nls")
public final class MorningstarDati
{
    public static final String ASSET_TYPE = "Asset Type";
    public static final String STOCK_STYLE = "Stock Style";
    public static final String STOCK_SECTOR = "Stock Sector";
    public static final String BOND_STYLE = "Bond Style";
    public static final String BOND_SECTOR = "Bond Sector";
    public static final String REGION = "Region";
    public static final String COUNTRY = "Country";
    public static final String HOLDING = "Holding";

    public static final List<String> TASSONOMIE = List.of(ASSET_TYPE, STOCK_STYLE, STOCK_SECTOR, BOND_STYLE,
                    BOND_SECTOR, REGION, COUNTRY, HOLDING);

    /** Tipi di fondo gestiti (modifica di Giacomo: anche Capital Preservation). */
    public static final List<String> TIPI_FONDO = List.of("Equity", "Fixed Income", "Allocation", "Commodities",
                    "Miscellaneous", "Capital Preservation");

    private static final Set<String> NON_CATEGORIE = Set.of("avgMarketCap", "portfolioDate", "name",
                    "masterPortfolioId", "14", "15", "16", "99");

    private static final Map<String, String> MAPPA_ASSET_TYPE = Map.of("1", "Stocks", "3", "Bonds", "7", "Cash", "2",
                    "Other", "4", "Other", "5", "Other", "6", "Other", "8", "Other", "99", "Not classified");

    private static final Map<String, String> MAPPA_BOND_STYLE = Map.of("1", "High Quality - Short Term", "2",
                    "High Quality - Intermediate Term", "3", "High Quality - Long Term", "4",
                    "Medium Quality - Short Term", "5", "Medium Quality - Intermediate Term", "6",
                    "Medium Quality - Long Term", "7", "Low Quality - Short Term", "8",
                    "Low Quality - Intermediate Term", "9", "Low Quality - Long Term");

    private static final Map<String, String> MAPPA_BOND_SECTOR = Map.of("10", "Government", "20", "Municipal", "30",
                    "Corporate", "40", "Securitized", "50", "Cash", "60", "Derivative");

    /** Da dove arrivano i dati di Morningstar (sito vero oppure test). */
    @FunctionalInterface
    public interface Fonte
    {
        /**
         * Restituisce la risposta JSON dell'API "securities" per l'ISIN e la
         * vista dati, oppure null se Morningstar non ha informazioni.
         */
        JsonElement leggi(String isin, String viewId) throws IOException;
    }

    /** Opzioni della classificazione. */
    public record Opzioni(boolean azioni, String holdingViewId, int maxHoldings)
    {
        /**
         * @param topHoldings
         *            quante posizioni dei fondi leggere: 0 (lascia quelle
         *            già presenti), 10, 25, 50, 100, 1000 o 3200
         */
        public static Opzioni di(boolean azioni, int topHoldings)
        {
            return switch (topHoldings)
            {
                case 0 -> new Opzioni(azioni, "Top10", 0);
                case 10 -> new Opzioni(azioni, "Top10", -1);
                case 25 -> new Opzioni(azioni, "Top25", -1);
                case 3200 -> new Opzioni(azioni, "Allholdings", -1);
                default -> new Opzioni(azioni, "Allholdings", topHoldings);
            };
        }
    }

    private MorningstarDati()
    {
    }

    /**
     * Classifica un titolo.
     *
     * @return per ogni tassonomia le categorie con la percentuale (0-100),
     *         oppure null se il titolo non è stato trovato o è stato saltato.
     *         Una tassonomia senza dati ha una mappa vuota.
     */
    public static Map<String, Map<String, Double>> classifica(String isin, Fonte fonte, Opzioni opzioni,
                    Consumer<String> log) throws IOException
    {
        var snapshot = primo(fonte.leggi(isin, "snapshot"));
        if (snapshot == null)
        {
            log.accept("  Nessuna informazione su Morningstar per l'ISIN " + isin + ": saltato");
            return null;
        }

        var tipo = testo(snapshot, "Type");
        var nome = testo(snapshot, "Name");
        if (nome != null)
            log.accept("  Nome su Morningstar: \"" + nome + "\"");

        if ("Fund".equals(tipo))
            return classificaFondo(isin, fonte, opzioni, log);

        if ("Stock".equals(tipo))
        {
            if (!opzioni.azioni())
            {
                log.accept("  È un'azione singola: saltata (attiva l'opzione per le azioni)");
                return null;
            }
            return classificaAzione(snapshot, log);
        }

        log.accept("  Tipo di titolo non supportato (" + tipo + "): saltato");
        return null;
    }

    private static Map<String, Map<String, Double>> classificaFondo(String isin, Fonte fonte, Opzioni opzioni,
                    Consumer<String> log) throws IOException
    {
        Map<String, JsonObject> cache = new HashMap<>();

        var it = leggi(fonte, cache, isin, "ITsnapshot");
        var fundType = it != null ? testo(oggetto(it, "CategoryBroadAssetClass"), "Name") : null;
        if (fundType == null)
        {
            log.accept("  Tipo di fondo non trovato: saltato");
            return null;
        }
        log.accept("  Tipo di fondo: " + fundType);

        Map<String, Map<String, Double>> grouping = nuovoGrouping();

        if (!TIPI_FONDO.contains(fundType))
        {
            log.accept("  Tipo di fondo non gestito: lascio le classificazioni che ci sono");
            return grouping;
        }

        var mappe = MorningstarMappe.get();
        double netEquity = 0;
        double netBonds = 0;

        for (var tassonomia : TASSONOMIE)
        {
            for (var secType : tipiDaLeggere(fundType, tassonomia))
            {
                var viewId = switch (tassonomia)
                {
                    case BOND_STYLE -> "ETFsnapshot";
                    case HOLDING -> opzioni.holdingViewId();
                    default -> "ITsnapshot";
                };

                var dati = leggi(fonte, cache, isin, viewId);
                if (dati == null)
                {
                    log.accept("  Attenzione: nessuna informazione su '" + tassonomia + "'");
                    continue;
                }

                var portfolio = primo(dati.get("Portfolios"));
                var valori = valori(portfolio, tassonomia, secType, opzioni);

                var chiave = HOLDING.equals(tassonomia) ? "SecurityName" : "Type";
                var percento = HOLDING.equals(tassonomia) ? "Weighting" : "Value";

                if (valori.isEmpty() || numero(valori.get(0), percento) == null)
                {
                    log.accept("  Attenzione: percentuali non trovate per '" + tassonomia + "'");
                    continue;
                }

                if (ASSET_TYPE.equals(tassonomia))
                {
                    for (var v : valori)
                    {
                        var codice = testo(v, chiave);
                        var p = numero(v, percento);
                        if ("1".equals(codice) && p != null)
                            netEquity = Math.min(1, p / 100);
                        if ("3".equals(codice) && p != null)
                            netBonds = Math.min(1, p / 100);
                    }
                }

                Map<String, String> mappa;
                if (REGION.equals(tassonomia) && "Bond".equals(secType))
                    mappa = mappe.regioniDaPaese();
                else
                    mappa = switch (tassonomia)
                    {
                        case ASSET_TYPE -> MAPPA_ASSET_TYPE;
                        case STOCK_STYLE -> mappe.stockStyle();
                        case STOCK_SECTOR -> mappe.stockSector();
                        case BOND_STYLE -> MAPPA_BOND_STYLE;
                        case BOND_SECTOR -> MAPPA_BOND_SECTOR;
                        case REGION -> mappe.regioni();
                        case COUNTRY -> mappe.paesi();
                        default -> null;
                    };

                List<String> categorie = new ArrayList<>();
                List<Double> percentuali = new ArrayList<>();
                List<String> nonMappate = new ArrayList<>();

                for (var v : valori)
                {
                    var codice = testo(v, chiave);
                    if (codice == null || NON_CATEGORIE.contains(codice))
                        continue;

                    String categoria;
                    if (mappa != null)
                    {
                        categoria = mappa.get(codice);
                        if (categoria == null)
                        {
                            nonMappate.add(codice);
                            continue;
                        }
                    }
                    else
                    {
                        categoria = codice.replace(",", "-");
                    }

                    // modifica di Giacomo: obbligazioni separate nei paesi
                    if ("Bond".equals(secType) && (COUNTRY.equals(tassonomia) || REGION.equals(tassonomia)))
                        categoria = categoria + " (Bonds)";

                    var p = numero(v, percento);
                    categorie.add(categoria);
                    percentuali.add(p != null ? p : 0d);
                }

                if (!nonMappate.isEmpty())
                    log.accept("  Attenzione: categorie sconosciute " + nonMappate + " in '" + tassonomia + "'");

                if (percentuali.isEmpty())
                    continue;

                double massimo;
                if (ASSET_TYPE.equals(tassonomia) || HOLDING.equals(tassonomia))
                    massimo = 1;
                else if (STOCK_STYLE.equals(tassonomia) || STOCK_SECTOR.equals(tassonomia)
                                || (isPaese(tassonomia) && "Equity".equals(secType)))
                    massimo = netEquity;
                else if (BOND_STYLE.equals(tassonomia) || BOND_SECTOR.equals(tassonomia)
                                || (isPaese(tassonomia) && "Bond".equals(secType)))
                    massimo = netBonds;
                else if ("Allocation".equals(fundType))
                    massimo = Math.min(1, netBonds + netEquity);
                else
                    massimo = 1;

                calcolaGrouping(grouping.get(tassonomia), tassonomia, categorie, percentuali, massimo, log);
            }
        }

        return grouping;
    }

    private static Map<String, Map<String, Double>> classificaAzione(JsonObject snapshot, Consumer<String> log)
    {
        var mappe = MorningstarMappe.get();
        Map<String, Map<String, Double>> grouping = nuovoGrouping();

        for (var tassonomia : TASSONOMIE)
        {
            String valore;
            Map<String, String> mappa;
            switch (tassonomia)
            {
                case ASSET_TYPE:
                    valore = testo(snapshot, "Type");
                    mappa = Map.of("Stock", "Stocks");
                    break;
                case STOCK_STYLE:
                    valore = cercaTesto(snapshot, "InvestmentStyle");
                    mappa = mappe.stockStyle();
                    break;
                case STOCK_SECTOR:
                    valore = testo(oggetto(snapshot, "Sector"), "SectorCode");
                    mappa = mappe.stockSector();
                    break;
                case REGION:
                    valore = testo(snapshot, "Country");
                    mappa = mappe.regioniDaPaese();
                    break;
                case COUNTRY:
                    valore = testo(snapshot, "Country");
                    mappa = mappe.paesi();
                    break;
                case HOLDING:
                    valore = testo(snapshot, "Name");
                    mappa = null;
                    break;
                default:
                    continue;
            }

            if (valore == null)
                continue;

            var categoria = mappa != null ? mappa.get(valore) : valore;
            if (categoria == null)
            {
                log.accept("  Attenzione: categoria sconosciuta [" + valore + "] in '" + tassonomia + "'");
                continue;
            }

            calcolaGrouping(grouping.get(tassonomia), tassonomia, List.of(categoria), List.of(100d), 1, log);
        }

        return grouping;
    }

    private static boolean isPaese(String tassonomia)
    {
        return COUNTRY.equals(tassonomia) || REGION.equals(tassonomia);
    }

    private static List<String> tipiDaLeggere(String fundType, String tassonomia)
    {
        boolean bond = BOND_STYLE.equals(tassonomia) || BOND_SECTOR.equals(tassonomia);
        boolean stock = STOCK_STYLE.equals(tassonomia) || STOCK_SECTOR.equals(tassonomia);

        return switch (fundType)
        {
            case "Equity" -> bond ? List.of() : List.of("Equity");
            case "Fixed Income", "Capital Preservation" -> stock ? List.of() : List.of("Bond");
            case "Allocation" -> isPaese(tassonomia) ? List.of("Equity", "Bond") : List.of("Mixed");
            case "Commodities" -> bond || stock ? List.of() : List.of("Commodities");
            case "Miscellaneous" -> bond || stock ? List.of() : List.of("Miscellaneous");
            default -> List.of("Unknown");
        };
    }

    private static List<JsonObject> valori(JsonObject portfolio, String tassonomia, String secType, Opzioni opzioni)
    {
        List<JsonObject> risultato = new ArrayList<>();
        if (portfolio == null)
            return risultato;

        switch (tassonomia)
        {
            case ASSET_TYPE:
                for (var a : oggetti(portfolio.get("AssetAllocations")))
                {
                    if ("MorningStarDefault".equals(testo(a, "Type")) && "N".equals(testo(a, "SalePosition")))
                    {
                        risultato.addAll(oggetti(a.get("BreakdownValues")));
                        break;
                    }
                }
                break;
            case STOCK_STYLE:
                aggiungiBreakdown(risultato, portfolio, "StyleBoxBreakdown", null);
                break;
            case STOCK_SECTOR:
                aggiungiBreakdown(risultato, portfolio, "GlobalStockSectorBreakdown", null);
                break;
            case BOND_STYLE:
                aggiungiBreakdown(risultato, portfolio, "BondStyleBoxBreakdown", null);
                break;
            case BOND_SECTOR:
                aggiungiBreakdown(risultato, portfolio, "GlobalBondSectorBreakdownLevel1", null);
                break;
            case REGION:
                if ("Bond".equals(secType))
                    aggiungiBreakdown(risultato, portfolio, "CountryExposure", secType);
                else
                    aggiungiBreakdown(risultato, portfolio, "RegionalExposure", null);
                break;
            case COUNTRY:
                aggiungiBreakdown(risultato, portfolio, "CountryExposure", secType);
                break;
            case HOLDING:
                for (var h : oggetti(portfolio.get("PortfolioHoldings")))
                    if (h.has("ISIN"))
                        risultato.add(h);
                int limite = opzioni.maxHoldings() >= 0 ? opzioni.maxHoldings() : 3200;
                if (risultato.size() > limite)
                    return new ArrayList<>(risultato.subList(0, limite));
                break;
            default:
        }

        if (risultato.size() > 3200)
            return new ArrayList<>(risultato.subList(0, 3200));
        return risultato;
    }

    private static void aggiungiBreakdown(List<JsonObject> risultato, JsonObject portfolio, String campo, String tipo)
    {
        for (var b : oggetti(portfolio.get(campo)))
        {
            if (!"N".equals(testo(b, "SalePosition")))
                continue;
            if (tipo != null && !tipo.equals(testo(b, "Type")))
                continue;
            risultato.addAll(oggetti(b.get("BreakdownValues")));
        }
    }

    /* package */ static void calcolaGrouping(Map<String, Double> grouping, String tassonomia,
                    List<String> categorie, List<Double> percentuali, double massimo, Consumer<String> log)
    {
        for (int i = 0; i < categorie.size(); i++)
            grouping.merge(categorie.get(i), percentuali.get(i) * massimo, Double::sum);

        for (var categoria : Set.copyOf(categorie))
        {
            if (grouping.get(categoria) > 100)
                log.accept(String.format(Locale.ITALIAN, "  Attenzione: '%s' in '%s' supera il 100%% (%.2f%%)",
                                categoria, tassonomia, grouping.get(categoria)));
        }

        for (var categoria : Set.copyOf(categorie))
        {
            double valore = grouping.get(categoria);
            if (valore >= 0)
                continue;

            if (valore > -0.25 && !isPaese(tassonomia) && !HOLDING.equals(tassonomia))
            {
                // piccolo valore negativo: lo si toglie dalla categoria oltre il
                // 100% (arrotondamenti di Morningstar)
                for (var altra : Set.copyOf(categorie))
                {
                    if (grouping.get(altra) > 100)
                    {
                        grouping.put(altra, grouping.get(altra) + grouping.get(categoria));
                        grouping.put(categoria, 0d);
                    }
                }
            }
            else
            {
                log.accept(String.format(Locale.ITALIAN,
                                "  Attenzione: valore negativo per '%s' in '%s' (%.2f%%): prodotto complesso, "
                                                + "valuta di classificarlo a mano",
                                categoria, tassonomia, valore));
            }
        }
    }

    private static Map<String, Map<String, Double>> nuovoGrouping()
    {
        Map<String, Map<String, Double>> grouping = new LinkedHashMap<>();
        for (var t : TASSONOMIE)
            grouping.put(t, new LinkedHashMap<>());
        return grouping;
    }

    private static JsonObject leggi(Fonte fonte, Map<String, JsonObject> cache, String isin, String viewId)
                    throws IOException
    {
        if (cache.containsKey(viewId))
            return cache.get(viewId);
        var dati = primo(fonte.leggi(isin, viewId));
        cache.put(viewId, dati);
        return dati;
    }

    // --- piccole funzioni per leggere il JSON ---

    private static JsonObject primo(JsonElement e)
    {
        if (e == null || e.isJsonNull())
            return null;
        if (e.isJsonObject())
            return e.getAsJsonObject();
        if (e.isJsonArray())
        {
            JsonArray a = e.getAsJsonArray();
            if (!a.isEmpty() && a.get(0).isJsonObject())
                return a.get(0).getAsJsonObject();
        }
        return null;
    }

    private static List<JsonObject> oggetti(JsonElement e)
    {
        List<JsonObject> risultato = new ArrayList<>();
        if (e == null || !e.isJsonArray())
            return risultato;
        for (var x : e.getAsJsonArray())
            if (x.isJsonObject())
                risultato.add(x.getAsJsonObject());
        return risultato;
    }

    private static JsonObject oggetto(JsonObject o, String campo)
    {
        if (o == null)
            return null;
        var e = o.get(campo);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String testo(JsonObject o, String campo)
    {
        if (o == null)
            return null;
        var e = o.get(campo);
        if (e == null || e.isJsonNull())
            return null;
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static Double numero(JsonObject o, String campo)
    {
        var t = testo(o, campo);
        if (t == null || t.isBlank())
            return null;
        try
        {
            return Double.parseDouble(t);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Come "$..campo": cerca il campo a qualunque profondità. */
    private static String cercaTesto(JsonElement e, String campo)
    {
        if (e == null)
            return null;
        if (e.isJsonObject())
        {
            var o = e.getAsJsonObject();
            if (o.has(campo))
                return testo(o, campo);
            for (var entry : o.entrySet())
            {
                var t = cercaTesto(entry.getValue(), campo);
                if (t != null)
                    return t;
            }
        }
        else if (e.isJsonArray())
        {
            for (var x : e.getAsJsonArray())
            {
                var t = cercaTesto(x, campo);
                if (t != null)
                    return t;
            }
        }
        return null;
    }
}
