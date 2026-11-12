package name.abuchen.portfolio.piano;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calcoli del piano di investimento. Riproduce le formule dei fogli "Calcoli",
 * "Piano" e "Parametri" (sezione 4) del file Excel.
 */
public final class PianoCalcolo
{
    /** Una riga del piano, così come viene mostrata all'utente. */
    public static final class Riga
    {
        private final int anno;
        private final int n;
        private final Double inflazione;
        private final Double pacMensile;
        private final Double targetNominale;
        private final Double valore;
        private final Double valoreReale;
        private final Double integrazione;
        private final boolean sottoTraiettoria;

        private Riga(int anno, int n, Double inflazione, Double pacMensile, Double targetNominale, Double valore,
                        Double valoreReale, Double integrazione, boolean sottoTraiettoria)
        {
            this.anno = anno;
            this.n = n;
            this.inflazione = inflazione;
            this.pacMensile = pacMensile;
            this.targetNominale = targetNominale;
            this.valore = valore;
            this.valoreReale = valoreReale;
            this.integrazione = integrazione;
            this.sottoTraiettoria = sottoTraiettoria;
        }

        public int getAnno()
        {
            return anno;
        }

        /** Numero dell'anno nel piano: 0 = anno di partenza. */
        public int getN()
        {
            return n;
        }

        /** Inflazione ISTAT inserita, oppure null. */
        public Double getInflazione()
        {
            return inflazione;
        }

        /** PAC mensile dell'anno, oppure null se non ancora noto. */
        public Double getPacMensile()
        {
            return pacMensile;
        }

        /** Target nominale al 31/12, oppure null se non ancora noto. */
        public Double getTargetNominale()
        {
            return targetNominale;
        }

        /** Valore del portafoglio al 31/12 inserito, oppure null. */
        public Double getValore()
        {
            return valore;
        }

        /** Valore reale (potere d'acquisto dell'anno di partenza). */
        public Double getValoreReale()
        {
            return valoreReale;
        }

        /** Integrazione di fine anno da versare, oppure null. */
        public Double getIntegrazione()
        {
            return integrazione;
        }

        /** true se il valore reale è sotto la traiettoria del target. */
        public boolean isSottoTraiettoria()
        {
            return sottoTraiettoria;
        }
    }

    private final List<Riga> righe;
    private final Integer annoRaggiungimento;
    private final Double pacNecessario;

    private PianoCalcolo(List<Riga> righe, Integer annoRaggiungimento, Double pacNecessario)
    {
        this.righe = Collections.unmodifiableList(righe);
        this.annoRaggiungimento = annoRaggiungimento;
        this.pacNecessario = pacNecessario;
    }

    public List<Riga> getRighe()
    {
        return righe;
    }

    /**
     * Anno stimato di raggiungimento dell'obiettivo, oppure null se oltre
     * l'orizzonte del piano.
     */
    public Integer getAnnoRaggiungimento()
    {
        return annoRaggiungimento;
    }

    /** Ultimo anno del piano. */
    public int getUltimoAnno()
    {
        return righe.get(righe.size() - 1).getAnno();
    }

    /**
     * PAC mensile necessario da subito per arrivare all'obiettivo entro l'anno
     * scelto, oppure null se l'anno scelto non è dopo l'anno di partenza.
     */
    public Double getPacNecessario()
    {
        return pacNecessario;
    }

    public static PianoCalcolo calcola(PianoInvestimento piano)
    {
        int ny = PianoInvestimento.NUMERO_ANNI;
        int start = piano.getAnnoPartenza();
        double r = piano.getRendimentoReale();
        double step = piano.getStepUp();

        double[] inflUsata = new double[ny + 1];
        double[] fattore = new double[ny + 1];
        double[] pac = new double[ny + 1];
        double[] targetReale = new double[ny + 1];
        double[] stimaReale = new double[ny + 1];

        List<Riga> righe = new ArrayList<>();

        for (int n = 0; n <= ny; n++)
        {
            int anno = start + n;
            Double infl = piano.getInflazione(anno);
            Double valore = piano.getValore(anno);

            inflUsata[n] = infl != null ? infl : piano.getInflazioneAttesa();

            if (n == 0)
            {
                fattore[n] = 1;
                pac[n] = piano.getPacMensile();
                targetReale[n] = piano.getValoreAttuale();
                stimaReale[n] = valore != null ? valore : piano.getValoreAttuale();
            }
            else
            {
                fattore[n] = fattore[n - 1] * (1 + inflUsata[n]);
                pac[n] = pac[n - 1] * (1 + inflUsata[n - 1]) * (1 + step);
                targetReale[n] = targetReale[n - 1] * (1 + r) + pac[n] * 12 / fattore[n];
                if (valore != null && infl != null)
                    stimaReale[n] = valore / fattore[n];
                else
                    stimaReale[n] = stimaReale[n - 1] * (1 + r) + pac[n] * 12 / fattore[n];
            }

            Double pacVisibile;
            Double target;
            Double valoreReale;

            if (n == 0)
            {
                pacVisibile = pac[n];
                target = targetReale[n];
                valoreReale = valore;
            }
            else
            {
                pacVisibile = piano.getInflazione(anno - 1) != null ? pac[n] : null;
                target = infl != null ? targetReale[n] * fattore[n] : null;
                valoreReale = valore != null && infl != null ? valore / fattore[n] : null;
            }

            Double integrazione = null;
            if (valore != null && target != null)
            {
                if (valore >= piano.getSogliaRibilanciamento())
                    integrazione = 0d;
                else
                    integrazione = Math.min(piano.getTettoIntegrazione(), Math.max(0, target - valore));
            }

            boolean sotto = valoreReale != null && valoreReale < targetReale[n];

            righe.add(new Riga(anno, n, infl, pacVisibile, target, valore, valoreReale, integrazione, sotto));
        }

        // anno stimato di raggiungimento: come nel foglio Excel si contano gli
        // anni in cui la stima del valore reale è sotto l'obiettivo
        int sotto = 0;
        for (int n = 0; n <= ny; n++)
            if (stimaReale[n] < piano.getObiettivo())
                sotto++;
        Integer annoRaggiungimento = sotto > ny ? null : start + sotto;

        return new PianoCalcolo(righe, annoRaggiungimento, pacNecessario(piano));
    }

    private static Double pacNecessario(PianoInvestimento piano)
    {
        int n = piano.getAnnoObiettivo() - piano.getAnnoPartenza();
        if (n <= 0)
            return null;

        double r = piano.getRendimentoReale();
        double g = piano.getStepUp();

        double rendita;
        if (Math.abs(r - g) < 0.000001)
            rendita = n * Math.pow(1 + r, n - 1d);
        else
            rendita = (Math.pow(1 + r, n) - Math.pow(1 + g, n)) / (r - g);

        double pac = (piano.getObiettivo() - piano.getValoreAttuale() * Math.pow(1 + r, n)) / rendita
                        / (12 * (1 + g));
        return Math.max(0, pac);
    }
}
