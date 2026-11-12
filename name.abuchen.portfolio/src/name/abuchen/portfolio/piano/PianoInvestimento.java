package name.abuchen.portfolio.piano;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dati del piano di investimento a lungo termine (fogli "Parametri" e "Piano"
 * del file Excel). I dati vengono salvati dentro il file di Portfolio
 * Performance come proprietà del client (vedi {@link PianoStore}).
 * <p>
 * Tutte le percentuali sono memorizzate come frazioni (0,02 = 2%), tranne il
 * tasso BCE che è in punti (3,75).
 */
public class PianoInvestimento
{
    /** Dati inseriti per un singolo anno del piano. */
    public static class AnnoPiano
    {
        private int anno;
        private Double inflazione;
        private Double valore;

        public AnnoPiano()
        {
        }

        public AnnoPiano(int anno)
        {
            this.anno = anno;
        }

        public int getAnno()
        {
            return anno;
        }

        /** Inflazione ISTAT dell'anno (frazione) oppure null se non inserita. */
        public Double getInflazione()
        {
            return inflazione;
        }

        public void setInflazione(Double inflazione)
        {
            this.inflazione = inflazione;
        }

        /** Valore del portafoglio al 31/12 oppure null se non inserito. */
        public Double getValore()
        {
            return valore;
        }

        public void setValore(Double valore)
        {
            this.valore = valore;
        }

        boolean isVuoto()
        {
            return inflazione == null && valore == null;
        }
    }

    /** Numero di anni del piano dopo l'anno di partenza. */
    public static final int NUMERO_ANNI = 45;

    // 1. formula quota azionaria
    private int eta = 33;
    private double tassoBce = 3.75;

    // 2. asset allocation (quote fisse)
    private double quotaCommodities = 0.05;
    private double quotaCash = 0.0;

    // 3. dati di partenza e parametri del piano
    private int annoPartenza = 2026;
    private double valoreAttuale = 35000;
    private double pacMensile = 800;
    private double inflazioneAttesa = 0.02;
    private double rendimentoReale = 0.042;
    private double stepUp = 0.0;
    private double obiettivo = 1_000_000;
    private double tettoIntegrazione = 4000;
    private double sogliaRibilanciamento = 150_000;
    private double tolleranza = 0.05;

    // 4. dove ti porta il piano
    private int annoObiettivo = 2050;

    private List<AnnoPiano> anni = new ArrayList<>();

    public int getEta()
    {
        return eta;
    }

    public void setEta(int eta)
    {
        this.eta = eta;
    }

    public double getTassoBce()
    {
        return tassoBce;
    }

    public void setTassoBce(double tassoBce)
    {
        this.tassoBce = tassoBce;
    }

    public double getQuotaCommodities()
    {
        return quotaCommodities;
    }

    public void setQuotaCommodities(double quotaCommodities)
    {
        this.quotaCommodities = quotaCommodities;
    }

    public double getQuotaCash()
    {
        return quotaCash;
    }

    public void setQuotaCash(double quotaCash)
    {
        this.quotaCash = quotaCash;
    }

    public int getAnnoPartenza()
    {
        return annoPartenza;
    }

    public void setAnnoPartenza(int annoPartenza)
    {
        this.annoPartenza = annoPartenza;
    }

    public double getValoreAttuale()
    {
        return valoreAttuale;
    }

    public void setValoreAttuale(double valoreAttuale)
    {
        this.valoreAttuale = valoreAttuale;
    }

    public double getPacMensile()
    {
        return pacMensile;
    }

    public void setPacMensile(double pacMensile)
    {
        this.pacMensile = pacMensile;
    }

    public double getInflazioneAttesa()
    {
        return inflazioneAttesa;
    }

    public void setInflazioneAttesa(double inflazioneAttesa)
    {
        this.inflazioneAttesa = inflazioneAttesa;
    }

    public double getRendimentoReale()
    {
        return rendimentoReale;
    }

    public void setRendimentoReale(double rendimentoReale)
    {
        this.rendimentoReale = rendimentoReale;
    }

    public double getStepUp()
    {
        return stepUp;
    }

    public void setStepUp(double stepUp)
    {
        this.stepUp = stepUp;
    }

    public double getObiettivo()
    {
        return obiettivo;
    }

    public void setObiettivo(double obiettivo)
    {
        this.obiettivo = obiettivo;
    }

    public double getTettoIntegrazione()
    {
        return tettoIntegrazione;
    }

    public void setTettoIntegrazione(double tettoIntegrazione)
    {
        this.tettoIntegrazione = tettoIntegrazione;
    }

    public double getSogliaRibilanciamento()
    {
        return sogliaRibilanciamento;
    }

    public void setSogliaRibilanciamento(double sogliaRibilanciamento)
    {
        this.sogliaRibilanciamento = sogliaRibilanciamento;
    }

    public double getTolleranza()
    {
        return tolleranza;
    }

    public void setTolleranza(double tolleranza)
    {
        this.tolleranza = tolleranza;
    }

    public int getAnnoObiettivo()
    {
        return annoObiettivo;
    }

    public void setAnnoObiettivo(int annoObiettivo)
    {
        this.annoObiettivo = annoObiettivo;
    }

    /** Quota azionaria Q = 125 − (Età + Tasso BCE × 5), come frazione. */
    public double getQuotaAzionaria()
    {
        return (125 - (eta + tassoBce * 5)) / 100;
    }

    /** Quota obbligazionaria: quello che resta dopo azioni e quote fisse. */
    public double getQuotaBond()
    {
        return 1 - getQuotaAzionaria() - quotaCommodities - quotaCash;
    }

    public Optional<AnnoPiano> getAnno(int anno)
    {
        if (anni == null)
            return Optional.empty();
        return anni.stream().filter(a -> a.getAnno() == anno).findFirst();
    }

    /** Restituisce i dati dell'anno, creandoli se non esistono. */
    public AnnoPiano getOrCreateAnno(int anno)
    {
        var existing = getAnno(anno);
        if (existing.isPresent())
            return existing.get();

        if (anni == null)
            anni = new ArrayList<>();

        var nuovo = new AnnoPiano(anno);
        anni.add(nuovo);
        anni.sort((a, b) -> Integer.compare(a.getAnno(), b.getAnno()));
        return nuovo;
    }

    public Double getInflazione(int anno)
    {
        return getAnno(anno).map(AnnoPiano::getInflazione).orElse(null);
    }

    public Double getValore(int anno)
    {
        return getAnno(anno).map(AnnoPiano::getValore).orElse(null);
    }

    public void setInflazione(int anno, Double inflazione)
    {
        getOrCreateAnno(anno).setInflazione(inflazione);
        pulisci();
    }

    public void setValore(int anno, Double valore)
    {
        getOrCreateAnno(anno).setValore(valore);
        pulisci();
    }

    private void pulisci()
    {
        anni.removeIf(AnnoPiano::isVuoto);
    }
}
