package name.abuchen.portfolio.piano;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Revisione annuale del PAC (fogli "Portafoglio" e "Revisione" del file
 * Excel): ripartisce il nuovo PAC mensile tra gli strumenti in proporzione a
 * quanto manca a ciascuno per tornare al peso target dopo 12 mesi di
 * versamenti.
 */
public final class PianoRevisione
{
    /** Stato di uno strumento rispetto al peso target. */
    public enum Stato
    {
        SOTTO_PESATO("sotto-pesato"), //$NON-NLS-1$
        IN_LINEA("in linea"), //$NON-NLS-1$
        SOVRA_PESATO("sovra-pesato"); //$NON-NLS-1$

        private final String label;

        Stato(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }
    }

    /** Dati di partenza di uno strumento: nome e controvalore attuale. */
    public record Input(String uuid, String nome, double controvalore)
    {
    }

    /** Una riga della revisione. */
    public static final class Riga
    {
        private final String uuid;
        private final String nome;
        private final String assetClass;
        private final double pesoNellaClasse;
        private final double controvalore;
        private final double pesoAttuale;
        private final double pesoTarget;
        private final Stato stato;
        private final Double nuovoPac;
        private final Double quotaPac;

        private Riga(String uuid, String nome, String assetClass, double pesoNellaClasse, double controvalore,
                        double pesoAttuale, double pesoTarget, Stato stato, Double nuovoPac, Double quotaPac)
        {
            this.uuid = uuid;
            this.nome = nome;
            this.assetClass = assetClass;
            this.pesoNellaClasse = pesoNellaClasse;
            this.controvalore = controvalore;
            this.pesoAttuale = pesoAttuale;
            this.pesoTarget = pesoTarget;
            this.stato = stato;
            this.nuovoPac = nuovoPac;
            this.quotaPac = quotaPac;
        }

        public String getUuid()
        {
            return uuid;
        }

        public String getNome()
        {
            return nome;
        }

        public String getAssetClass()
        {
            return assetClass;
        }

        public double getPesoNellaClasse()
        {
            return pesoNellaClasse;
        }

        public double getControvalore()
        {
            return controvalore;
        }

        public double getPesoAttuale()
        {
            return pesoAttuale;
        }

        public double getPesoTarget()
        {
            return pesoTarget;
        }

        public double getScostamento()
        {
            return pesoAttuale - pesoTarget;
        }

        public Stato getStato()
        {
            return stato;
        }

        /** Nuovo PAC mensile dello strumento, null se non ancora noto. */
        public Double getNuovoPac()
        {
            return nuovoPac;
        }

        /** Quota del nuovo PAC, null se non ancora nota. */
        public Double getQuotaPac()
        {
            return quotaPac;
        }
    }

    private final int anno;
    private final double pacInCorso;
    private final Double inflazione;
    private final Double nuovoPac;
    private final double totaleControvalore;
    private final double totalePesoTarget;
    private final List<Riga> righe;

    private PianoRevisione(int anno, double pacInCorso, Double inflazione, Double nuovoPac, double totaleControvalore,
                    double totalePesoTarget, List<Riga> righe)
    {
        this.anno = anno;
        this.pacInCorso = pacInCorso;
        this.inflazione = inflazione;
        this.nuovoPac = nuovoPac;
        this.totaleControvalore = totaleControvalore;
        this.totalePesoTarget = totalePesoTarget;
        this.righe = Collections.unmodifiableList(righe);
    }

    public int getAnno()
    {
        return anno;
    }

    public double getPacInCorso()
    {
        return pacInCorso;
    }

    /** Inflazione dell'anno inserita nel Prospetto, oppure null. */
    public Double getInflazione()
    {
        return inflazione;
    }

    /**
     * Nuovo PAC mensile da gennaio, oppure null se manca l'inflazione
     * dell'anno.
     */
    public Double getNuovoPac()
    {
        return nuovoPac;
    }

    public double getTotaleControvalore()
    {
        return totaleControvalore;
    }

    public double getTotalePesoTarget()
    {
        return totalePesoTarget;
    }

    public List<Riga> getRighe()
    {
        return righe;
    }

    /**
     * Calcola la revisione dell'anno dato.
     *
     * @param input
     *            strumenti con il loro controvalore attuale
     */
    public static PianoRevisione calcola(PianoInvestimento piano, int anno, List<Input> input)
    {
        var pacPerAnno = PianoCalcolo.calcola(piano).getPacTuttiGliAnni();
        double pacInCorso = pacPerAnno.getOrDefault(anno, piano.getPacMensile());
        Double nuovoPac = pacPerAnno.get(anno + 1);

        var inflazione = piano.getInflazione(anno);
        if (inflazione == null)
            nuovoPac = null;
        else if (nuovoPac == null)
            nuovoPac = pacInCorso;

        double totale = input.stream().mapToDouble(Input::controvalore).sum();
        double pacAnnuo = nuovoPac != null ? nuovoPac * 12 : 0;
        double t = totale + pacAnnuo;

        double[] target = new double[input.size()];
        double[] gap = new double[input.size()];
        double sommaGap = 0;
        double totaleTarget = 0;

        for (int i = 0; i < input.size(); i++)
        {
            var in = input.get(i);
            var strumento = piano.getStrumento(in.uuid());
            target[i] = strumento.map(s -> s.getPesoNellaClasse() * piano.getPesoAssetClass(s.getAssetClass()))
                            .orElse(0d);
            totaleTarget += target[i];
            gap[i] = t * target[i] - in.controvalore();
            if (gap[i] > 0)
                sommaGap += gap[i];
        }

        List<Riga> righe = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
        {
            var in = input.get(i);
            var strumento = piano.getStrumento(in.uuid());

            double pesoAttuale = totale == 0 ? 0 : in.controvalore() / totale;
            double scostamento = pesoAttuale - target[i];
            Stato stato;
            if (scostamento < -piano.getTolleranza())
                stato = Stato.SOTTO_PESATO;
            else if (scostamento > piano.getTolleranza())
                stato = Stato.SOVRA_PESATO;
            else
                stato = Stato.IN_LINEA;

            Double pac = null;
            Double quota = null;
            if (nuovoPac != null)
            {
                if (sommaGap == 0)
                    pac = nuovoPac * target[i];
                else
                    pac = nuovoPac * Math.max(0, gap[i]) / sommaGap;
                quota = nuovoPac == 0 ? 0 : pac / nuovoPac;
            }

            righe.add(new Riga(in.uuid(), in.nome(), strumento.map(PianoInvestimento.Strumento::getAssetClass).orElse(null),
                            strumento.map(PianoInvestimento.Strumento::getPesoNellaClasse).orElse(0d),
                            in.controvalore(), pesoAttuale, target[i], stato, pac, quota));
        }

        return new PianoRevisione(anno, pacInCorso, inflazione, nuovoPac, totale, totaleTarget, righe);
    }
}
