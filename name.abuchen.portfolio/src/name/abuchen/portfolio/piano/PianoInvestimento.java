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

    /** Una asset class con il suo peso target nel portafoglio. */
    public static class AssetClass
    {
        private String nome;
        private double peso;

        public AssetClass()
        {
        }

        public AssetClass(String nome, double peso)
        {
            this.nome = nome;
            this.peso = peso;
        }

        public String getNome()
        {
            return nome;
        }

        public void setNome(String nome)
        {
            this.nome = nome;
        }

        public double getPeso()
        {
            return peso;
        }

        public void setPeso(double peso)
        {
            this.peso = peso;
        }
    }

    /**
     * Assegnazione di un titolo di Portfolio Performance a una asset class,
     * con il suo peso dentro l'asset class (foglio Portafoglio dell'Excel).
     */
    public static class Strumento
    {
        private String uuid;
        private String assetClass;
        private double pesoNellaClasse;

        public Strumento()
        {
        }

        public Strumento(String uuid)
        {
            this.uuid = uuid;
        }

        public String getUuid()
        {
            return uuid;
        }

        public String getAssetClass()
        {
            return assetClass;
        }

        public void setAssetClass(String assetClass)
        {
            this.assetClass = assetClass;
        }

        public double getPesoNellaClasse()
        {
            return pesoNellaClasse;
        }

        public void setPesoNellaClasse(double pesoNellaClasse)
        {
            this.pesoNellaClasse = pesoNellaClasse;
        }
    }

    /** Numero massimo di asset class. */
    public static final int NUMERO_ASSET_CLASS = 6;

    /** Numero di anni del piano dopo l'anno di partenza. */
    public static final int NUMERO_ANNI = 45;

    // 1. formula quota azionaria
    private int eta = 33;
    private double tassoBce = 3.75;

    // 2. asset allocation: fino a sei asset class con peso libero (la somma
    // deve fare 100%). I due campi seguenti servono solo a leggere i file
    // salvati con la prima versione, in cui Equity e Bond venivano dalla
    // formula e le altre quote erano fisse.
    private Double quotaCommodities;
    private Double quotaCash;
    private List<AssetClass> assetClass;

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

    private int annoRevisione;

    private List<AnnoPiano> anni = new ArrayList<>();

    private List<Strumento> strumenti = new ArrayList<>();

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

    /**
     * Asset class del piano. Se il file è stato salvato con la prima versione
     * vengono create dai valori di allora (Equity dalla formula, Bond il
     * resto, Commodities e Cash fisse).
     */
    public List<AssetClass> getAssetClass()
    {
        if (assetClass == null)
        {
            assetClass = new ArrayList<>();
            double commodities = quotaCommodities != null ? quotaCommodities : 0.05;
            double cash = quotaCash != null ? quotaCash : 0;
            double equity = getQuotaAzionaria();
            assetClass.add(new AssetClass("Equity", equity)); //$NON-NLS-1$
            assetClass.add(new AssetClass("Bond", 1 - equity - commodities - cash)); //$NON-NLS-1$
            assetClass.add(new AssetClass("Commodities", commodities)); //$NON-NLS-1$
            assetClass.add(new AssetClass("Cash", cash)); //$NON-NLS-1$
            quotaCommodities = null;
            quotaCash = null;
        }
        while (assetClass.size() < NUMERO_ASSET_CLASS)
            assetClass.add(new AssetClass("", 0)); //$NON-NLS-1$
        return assetClass;
    }

    /** Somma dei pesi delle asset class. */
    public double getTotaleAssetClass()
    {
        return getAssetClass().stream().mapToDouble(AssetClass::getPeso).sum();
    }

    /** Peso target dell'asset class con il nome dato, 0 se non esiste. */
    public double getPesoAssetClass(String nome)
    {
        if (nome == null || nome.isBlank())
            return 0;
        return getAssetClass().stream().filter(a -> nome.equals(a.getNome())).mapToDouble(AssetClass::getPeso)
                        .findFirst().orElse(0);
    }

    /** Anno scelto nella Revisione, 0 se non ancora scelto. */
    public int getAnnoRevisione()
    {
        return annoRevisione;
    }

    public void setAnnoRevisione(int annoRevisione)
    {
        this.annoRevisione = annoRevisione;
    }

    public Optional<Strumento> getStrumento(String uuid)
    {
        if (strumenti == null)
            return Optional.empty();
        return strumenti.stream().filter(s -> uuid.equals(s.getUuid())).findFirst();
    }

    /** Restituisce l'assegnazione del titolo, creandola se non esiste. */
    public Strumento getOrCreateStrumento(String uuid)
    {
        var existing = getStrumento(uuid);
        if (existing.isPresent())
            return existing.get();
        if (strumenti == null)
            strumenti = new ArrayList<>();
        var nuovo = new Strumento(uuid);
        strumenti.add(nuovo);
        return nuovo;
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
