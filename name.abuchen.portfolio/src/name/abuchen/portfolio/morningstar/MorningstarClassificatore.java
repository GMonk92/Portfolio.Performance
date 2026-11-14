package name.abuchen.portfolio.morningstar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;

/**
 * Classificatore Morningstar: legge la composizione dei titoli e aggiorna le
 * tassonomie del file (Asset Type, Stock Style, Stock Sector, Bond Style, Bond
 * Sector, Region, Country, Holding), come lo script pp-portfolio-classifier.
 * <p>
 * Nella nota del titolo si possono scrivere i comandi dello script:
 * <code>#PPC:SKIP</code> per saltarlo e <code>#PPC:[ISIN2=...]</code> per
 * usare un altro ISIN quando per il primo mancano dei dati.
 */
@SuppressWarnings("nls")
public final class MorningstarClassificatore
{
    private static final Pattern ISIN2 = Pattern.compile("#PPC:\\[ISIN2=([A-Z0-9]{12})");

    /** Dati letti per un titolo. */
    public static final class Risultato
    {
        private final Security security;
        private final Map<String, Map<String, Double>> grouping;
        private final Map<String, Map<String, Double>> alternativo;

        public Risultato(Security security, Map<String, Map<String, Double>> grouping,
                        Map<String, Map<String, Double>> alternativo)
        {
            this.security = security;
            this.grouping = grouping;
            this.alternativo = alternativo;
        }

        public Security getSecurity()
        {
            return security;
        }

        public Map<String, Map<String, Double>> getGrouping()
        {
            return grouping;
        }

        Map<String, Double> perTassonomia(String tassonomia, Consumer<String> log)
        {
            var g = grouping.getOrDefault(tassonomia, Map.of());
            if (g.isEmpty() && alternativo != null)
            {
                var alt = alternativo.getOrDefault(tassonomia, Map.of());
                if (!alt.isEmpty())
                {
                    log.accept("  '" + security.getName() + "': per '" + tassonomia + "' uso l'ISIN alternativo");
                    return alt;
                }
            }
            return g;
        }
    }

    private MorningstarClassificatore()
    {
    }

    /**
     * Prima fase: legge i dati da Morningstar (non modifica il file, si può
     * eseguire in background).
     */
    public static List<Risultato> leggi(Client client, MorningstarDati.Fonte fonte, MorningstarDati.Opzioni opzioni,
                    Consumer<String> log, BooleanSupplier annullato)
    {
        List<Risultato> risultati = new ArrayList<>();

        var titoli = new ArrayList<>(client.getSecurities());
        titoli.sort(Comparator.comparing(s -> s.getName() == null ? "" : s.getName().toUpperCase(Locale.ROOT)));

        for (var security : titoli)
        {
            if (annullato.getAsBoolean())
            {
                log.accept("\nInterrotto.");
                break;
            }

            log.accept("\n[" + security.getName() + "]");

            var isin = security.getIsin();
            if (isin == null || isin.isBlank())
            {
                log.accept("  Nessun ISIN: saltato");
                continue;
            }
            if (security.isRetired())
            {
                log.accept("  Titolo inattivo: saltato");
                continue;
            }

            var nota = security.getNote() != null ? security.getNote() : "";
            if (nota.contains("#PPC:SKIP"))
            {
                log.accept("  Nella nota c'è #PPC:SKIP: saltato");
                continue;
            }

            try
            {
                var grouping = MorningstarDati.classifica(isin.trim(), fonte, opzioni, log);
                if (grouping == null)
                    continue;

                Map<String, Map<String, Double>> alternativo = null;
                var m = ISIN2.matcher(nota);
                if (m.find())
                {
                    log.accept("  ISIN alternativo " + m.group(1));
                    alternativo = MorningstarDati.classifica(m.group(1), fonte, opzioni, log);
                }

                risultati.add(new Risultato(security, grouping, alternativo));
            }
            catch (IOException | RuntimeException e)
            {
                log.accept("  Errore nella lettura da Morningstar: " + e.getMessage());
            }
        }

        return risultati;
    }

    /**
     * Seconda fase: scrive le classificazioni nelle tassonomie del file.
     *
     * @return numero di tassonomie aggiornate o create
     */
    public static int applica(Client client, List<Risultato> risultati, Consumer<String> log)
    {
        int aggiornate = 0;

        for (var nome : MorningstarDati.TASSONOMIE)
        {
            var colori = new Colori();

            List<Taxonomy> tassonomie = client.getTaxonomies().stream().filter(t -> nome.equals(t.getName()))
                            .toList();

            if (tassonomie.isEmpty())
            {
                boolean serve = MorningstarDati.ASSET_TYPE.equals(nome) || MorningstarDati.REGION.equals(nome)
                                || MorningstarDati.COUNTRY.equals(nome)
                                || risultati.stream().anyMatch(r -> r.perTassonomia(nome, s -> {
                                }).values().stream().anyMatch(v -> v != 0));
                if (!serve)
                    continue;

                var taxonomy = new Taxonomy(nome);
                taxonomy.setRootNode(new Classification(UUID.randomUUID().toString(), nome));
                client.addTaxonomy(taxonomy);
                tassonomie = List.of(taxonomy);
                log.accept("\nTassonomia '" + nome + "' creata");
            }
            else
            {
                log.accept("\nTassonomia '" + nome + "' aggiornata");
            }

            for (var taxonomy : tassonomie)
                applica(taxonomy.getRoot(), nome, risultati, colori, log);

            aggiornate++;
        }

        return aggiornate;
    }

    private static void applica(Classification root, String nome, List<Risultato> risultati, Colori colori,
                    Consumer<String> log)
    {
        int rank = 0;

        for (var risultato : risultati)
        {
            var security = risultato.getSecurity();
            var assegnazioni = risultato.perTassonomia(nome, log);

            if (assegnazioni.isEmpty())
                continue; // nessun dato: restano le classificazioni che ci sono

            // azzera le assegnazioni del titolo: quelle rimaste a zero
            // vengono tolte alla fine
            for (var child : root.getChildren())
                for (var a : child.getAssignments())
                    if (a.getInvestmentVehicle() == security)
                        a.setWeight(0);

            // piccoli arrotondamenti: somma tra 100,01% e 100,05%
            double scala = 1;
            int sommaIniziale = 0;
            while (true)
            {
                int somma = 0;
                for (var w : assegnazioni.values())
                {
                    int peso = (int) Math.min(Classification.ONE_HUNDRED_PERCENT, Math.round(w * 100 * scala));
                    if (peso > 0)
                        somma += peso;
                }
                if (sommaIniziale == 0)
                    sommaIniziale = somma;
                if (somma > 10000 && somma < 10006)
                    scala = scala * 0.999999;
                else
                    break;
            }

            int somma = 0;
            for (var entry : assegnazioni.entrySet())
            {
                int peso = (int) Math.round(entry.getValue() * 100 * scala);
                var categoria = pulisci(entry.getKey());

                var child = root.getChildren().stream().filter(c -> categoria.equals(c.getName())).findFirst()
                                .orElse(null);

                boolean crea = !(peso == 0 && (MorningstarDati.HOLDING.equals(nome)
                                || MorningstarDati.COUNTRY.equals(nome)));
                if (child == null && crea)
                {
                    child = new Classification(root, UUID.randomUUID().toString(), categoria, colori.prossimo());
                    child.setWeight(0);
                    child.setRank(root.getChildren().size());
                    root.addChild(child);
                }

                if (child == null || peso == 0)
                    continue;

                if (peso < 0)
                {
                    log.accept(String.format(Locale.ITALIAN,
                                    "  Attenzione: peso negativo ignorato per '%s' in '%s' (%.2f%%)",
                                    security.getName(), categoria, peso / 100d));
                    continue;
                }

                if (peso > Classification.ONE_HUNDRED_PERCENT)
                {
                    log.accept(String.format(Locale.ITALIAN,
                                    "  Attenzione: peso oltre il 100%% ridotto a 100%% per '%s' in '%s'",
                                    security.getName(), categoria));
                    peso = Classification.ONE_HUNDRED_PERCENT;
                }

                Assignment assignment = null;
                for (var a : child.getAssignments())
                    if (a.getInvestmentVehicle() == security)
                        assignment = a;

                if (assignment == null)
                {
                    assignment = new Assignment(security, 0);
                    assignment.setRank(++rank);
                    child.addAssignment(assignment);
                }

                assignment.setWeight(peso);
                somma += peso;
            }

            if (scala != 1)
                log.accept(String.format(Locale.ITALIAN, "  '%s' in '%s': somma corretta a %.2f%% (era %.2f%%)",
                                security.getName(), nome, somma / 100d, sommaIniziale / 100d));
            if (somma > Classification.ONE_HUNDRED_PERCENT)
                log.accept(String.format(Locale.ITALIAN,
                                "  Attenzione: per '%s' in '%s' la somma supera il 100%% (%.2f%%)",
                                security.getName(), nome, somma / 100d));
        }

        // toglie le assegnazioni rimaste a zero
        for (var child : root.getChildren())
            for (var a : new ArrayList<>(child.getAssignments()))
                if (a.getWeight() == 0)
                    child.removeAssignment(a);
    }

    /** Toglie le entità HTML dai nomi (es. "&amp;"), come clean_text. */
    /* package */ static String pulisci(String testo)
    {
        return testo.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                        .replace("&apos;", "'").replace("&amp;", "&").trim();
    }

    private static final class Colori
    {
        private final List<String> lista = MorningstarMappe.get().colori();
        private int indice;

        String prossimo()
        {
            var c = lista.get(indice % lista.size());
            indice++;
            return c;
        }
    }
}
