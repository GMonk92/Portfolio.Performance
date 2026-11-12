package name.abuchen.portfolio.ui.views.piano;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.piano.PianoCalcolo;
import name.abuchen.portfolio.piano.PianoInvestimento;
import name.abuchen.portfolio.piano.PianoStore;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.Colors;

/**
 * Sezione "Parametri" del piano di investimento: corrisponde al foglio
 * Parametri del file Excel.
 */
@SuppressWarnings("nls")
public class ParametriPianoView extends AbstractFinanceView
{
    @FunctionalInterface
    private interface Setter
    {
        void set(String text) throws ParseException;
    }

    private record Campo(Text text, Supplier<String> getter)
    {
    }

    private record Risultato(Label label, Supplier<String> getter)
    {
    }

    @Inject
    private ExchangeRateProviderFactory factory;

    private PianoInvestimento piano;

    private final List<Campo> campi = new ArrayList<>();
    private final List<Risultato> risultati = new ArrayList<>();

    private Composite body;

    @Override
    protected String getDefaultTitle()
    {
        return "Parametri del piano";
    }

    @Override
    protected Control createBody(Composite parent)
    {
        piano = PianoStore.load(getClient());

        var scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        body = new Composite(scrolled, SWT.NONE);
        body.setBackground(Colors.theme().defaultBackground());
        GridLayoutFactory.fillDefaults().numColumns(3).margins(10, 10).spacing(10, 4).applyTo(body);

        nota(body, "Scrivi i valori nei campi e premi Invio (o passa al campo successivo): il piano si salva nel file di Portfolio Performance.");

        sezione(body, "1. Formula quota azionaria");
        campo(body, "Età (anni)", () -> PianoFormat.intero(piano.getEta()),
                        t -> piano.setEta(intero(t)), "");
        campo(body, "Tasso BCE di riferimento (in punti, es. 3,75)", () -> PianoFormat.numero(piano.getTassoBce()),
                        t -> piano.setTassoBce(numero(t)), "");
        risultato(body, "Quota azionaria  Q = 125 − (Età + Tasso × 5)",
                        () -> PianoFormat.percentuale(piano.getQuotaAzionaria()), true);

        sezione(body, "2. Asset allocation");
        risultato(body, "Equity", () -> PianoFormat.percentuale(piano.getQuotaAzionaria()), false);
        risultato(body, "Bond (quello che resta)", () -> PianoFormat.percentuale(piano.getQuotaBond()), false);
        campo(body, "Commodities (quota fissa, %)", () -> PianoFormat.percentualeModifica(piano.getQuotaCommodities()),
                        t -> piano.setQuotaCommodities(percentuale(t)), "");
        campo(body, "Cash (quota fissa, %)", () -> PianoFormat.percentualeModifica(piano.getQuotaCash()),
                        t -> piano.setQuotaCash(percentuale(t)), "");
        var avviso = new Label(body, SWT.NONE);
        avviso.setForeground(Colors.theme().redForeground());
        avviso.setBackground(body.getBackground());
        GridDataFactory.fillDefaults().span(3, 1).applyTo(avviso);
        risultati.add(new Risultato(avviso,
                        () -> piano.getQuotaBond() < 0 ? "Controlla le quote fisse: la quota Bond è negativa." : ""));

        sezione(body, "3. Dati di partenza e parametri del piano");
        campo(body, "Anno di partenza (anno 0)", () -> PianoFormat.intero(piano.getAnnoPartenza()),
                        t -> piano.setAnnoPartenza(intero(t)), "");
        var valore = campo(body, "Valore attuale del portafoglio (€)",
                        () -> PianoFormat.numero(piano.getValoreAttuale()), t -> piano.setValoreAttuale(numero(t)),
                        null);
        var usaPP = new Button(body, SWT.PUSH);
        usaPP.setText("Prendi il valore da Portfolio Performance");
        usaPP.setToolTipText("Usa il valore di mercato di oggi dei titoli presenti in Portfolio Performance");
        usaPP.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            piano.setValoreAttuale(valoreTitoli(LocalDate.now()));
            valore.setText(PianoFormat.numero(piano.getValoreAttuale()));
            salvaEAggiorna();
        }));

        campo(body, "PAC mensile in corso (€)", () -> PianoFormat.numero(piano.getPacMensile()),
                        t -> piano.setPacMensile(numero(t)), "");
        campo(body, "Inflazione attesa per gli anni futuri (%)",
                        () -> PianoFormat.percentualeModifica(piano.getInflazioneAttesa()),
                        t -> piano.setInflazioneAttesa(percentuale(t)), "");
        campo(body, "Rendimento reale atteso, al netto dell'inflazione (%)",
                        () -> PianoFormat.percentualeModifica(piano.getRendimentoReale()),
                        t -> piano.setRendimentoReale(percentuale(t)), "");
        campo(body, "Aumento reale annuo del PAC oltre l'inflazione, step-up (%)",
                        () -> PianoFormat.percentualeModifica(piano.getStepUp()), t -> piano.setStepUp(percentuale(t)),
                        "");
        campo(body, "Obiettivo in euro reali, potere d'acquisto di oggi (€)",
                        () -> PianoFormat.numero(piano.getObiettivo()), t -> piano.setObiettivo(numero(t)), "");
        campo(body, "Tetto massimo integrazione annua (€)", () -> PianoFormat.numero(piano.getTettoIntegrazione()),
                        t -> piano.setTettoIntegrazione(numero(t)), "");
        campo(body, "Soglia oltre cui ribilanciare internamente invece di integrare (€)",
                        () -> PianoFormat.numero(piano.getSogliaRibilanciamento()),
                        t -> piano.setSogliaRibilanciamento(numero(t)), "");
        campo(body, "Tolleranza scostamento dal peso target, ± (%)",
                        () -> PianoFormat.percentualeModifica(piano.getTolleranza()),
                        t -> piano.setTolleranza(percentuale(t)), "");

        sezione(body, "4. Dove ti porta il piano");
        risultato(body, "Anno stimato di raggiungimento dell'obiettivo", () -> {
            var calcolo = PianoCalcolo.calcola(piano);
            var anno = calcolo.getAnnoRaggiungimento();
            return anno != null ? String.valueOf(anno) : "oltre " + calcolo.getUltimoAnno();
        }, true);
        risultato(body, "Anni necessari", () -> {
            var anno = PianoCalcolo.calcola(piano).getAnnoRaggiungimento();
            return anno != null ? String.valueOf(anno - piano.getAnnoPartenza()) : "-";
        }, false);
        campo(body, "Anno entro cui vorresti arrivarci", () -> PianoFormat.intero(piano.getAnnoObiettivo()),
                        t -> piano.setAnnoObiettivo(intero(t)), "");
        risultato(body, "PAC mensile necessario da subito per arrivarci", () -> {
            var pac = PianoCalcolo.calcola(piano).getPacNecessario();
            return pac != null ? PianoFormat.euro(pac) : "-";
        }, true);
        var differenza = risultato(body, "Differenza rispetto al PAC in corso (al mese)", () -> {
            var pac = PianoCalcolo.calcola(piano).getPacNecessario();
            return pac != null ? PianoFormat.euro(pac - piano.getPacMensile()) : "-";
        }, false);
        differenza.label().addListener(SWT.Paint, e -> {
            var pac = PianoCalcolo.calcola(piano).getPacNecessario();
            var rosso = pac != null && pac - piano.getPacMensile() > 0.5;
            var colore = rosso ? Colors.theme().redForeground() : Colors.theme().defaultForeground();
            if (!colore.equals(differenza.label().getForeground()))
                differenza.label().setForeground(colore);
        });

        aggiornaRisultati();

        scrolled.setContent(body);
        scrolled.setMinSize(body.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        return scrolled;
    }

    @Override
    public void notifyModelUpdated()
    {
        if (body == null || body.isDisposed())
            return;

        piano = PianoStore.load(getClient());
        for (var campo : campi)
            if (!campo.text().isDisposed())
                campo.text().setText(campo.getter().get());
        aggiornaRisultati();
    }

    private double valoreTitoli(LocalDate date)
    {
        var converter = new CurrencyConverterImpl(factory, getClient().getBaseCurrency());
        var snapshot = ClientSnapshot.create(getClient(), converter, date);
        return PianoFormat.fromMoney(snapshot.getJointPortfolio().getValue());
    }

    private void sezione(Composite parent, String testo)
    {
        var label = new Label(parent, SWT.NONE);
        label.setText(testo);
        label.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.HEADING2);
        label.setBackground(parent.getBackground());
        GridDataFactory.fillDefaults().span(3, 1).indent(0, 12).applyTo(label);
    }

    private void nota(Composite parent, String testo)
    {
        var label = new Label(parent, SWT.WRAP);
        label.setText(testo);
        label.setForeground(Colors.theme().grayForeground());
        label.setBackground(parent.getBackground());
        GridDataFactory.fillDefaults().span(3, 1).hint(600, SWT.DEFAULT).applyTo(label);
    }

    /**
     * Crea una riga con un campo da compilare. Se nota è null la terza colonna
     * resta libera per un altro controllo.
     */
    private Text campo(Composite parent, String etichetta, Supplier<String> getter, Setter setter, String nota)
    {
        var label = new Label(parent, SWT.NONE);
        label.setText(etichetta);
        label.setBackground(parent.getBackground());

        var text = new Text(parent, SWT.BORDER | SWT.RIGHT);
        text.setText(getter.get());
        text.setForeground(Colors.BLUE);
        GridDataFactory.fillDefaults().hint(120, SWT.DEFAULT).applyTo(text);

        Runnable applica = () -> {
            var prima = getter.get();
            try
            {
                setter.set(text.getText());
            }
            catch (ParseException | IllegalArgumentException e)
            {
                // valore non valido: si torna al valore precedente
            }
            var dopo = getter.get();
            if (!dopo.equals(text.getText()))
                text.setText(dopo);
            if (!dopo.equals(prima))
                salvaEAggiorna();
        };

        text.addListener(SWT.DefaultSelection, e -> applica.run());
        text.addListener(SWT.FocusOut, e -> applica.run());

        if (nota != null)
        {
            var n = new Label(parent, SWT.NONE);
            n.setText(nota);
            n.setBackground(parent.getBackground());
        }

        campi.add(new Campo(text, getter));
        return text;
    }

    private Risultato risultato(Composite parent, String etichetta, Supplier<String> getter, boolean grassetto)
    {
        var label = new Label(parent, SWT.NONE);
        label.setText(etichetta);
        label.setBackground(parent.getBackground());

        var value = new Label(parent, SWT.RIGHT);
        value.setBackground(parent.getBackground());
        if (grassetto)
            value.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
        GridDataFactory.fillDefaults().hint(120, SWT.DEFAULT).applyTo(value);

        var filler = new Label(parent, SWT.NONE);
        filler.setBackground(parent.getBackground());

        var risultato = new Risultato(value, getter);
        risultati.add(risultato);
        return risultato;
    }

    private void salvaEAggiorna()
    {
        PianoStore.save(getClient(), piano);
        aggiornaRisultati();
    }

    private void aggiornaRisultati()
    {
        for (var r : risultati)
        {
            if (r.label().isDisposed())
                continue;
            r.label().setText(r.getter().get());
            r.label().redraw();
        }
        body.layout(true);
    }

    private static int intero(String text) throws ParseException
    {
        var value = PianoFormat.leggi(text);
        if (value == null)
            throw new ParseException(text, 0);
        return (int) Math.round(value);
    }

    private static double numero(String text) throws ParseException
    {
        var value = PianoFormat.leggi(text);
        if (value == null)
            throw new ParseException(text, 0);
        return value;
    }

    private static double percentuale(String text) throws ParseException
    {
        var value = PianoFormat.leggiPercentuale(text);
        if (value == null)
            throw new ParseException(text, 0);
        return value;
    }
}
