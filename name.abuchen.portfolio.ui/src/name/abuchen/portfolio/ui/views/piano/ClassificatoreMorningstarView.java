package name.abuchen.portfolio.ui.views.piano;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.morningstar.MorningstarClassificatore;
import name.abuchen.portfolio.morningstar.MorningstarDati;
import name.abuchen.portfolio.morningstar.MorningstarWeb;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.Colors;

/**
 * Pagina "Classificatore Morningstar": fa dentro il programma quello che
 * faceva lo script Python pp-portfolio-classifier, con le modifiche di
 * Giacomo già incluse.
 */
@SuppressWarnings("nls")
public class ClassificatoreMorningstarView extends AbstractFinanceView
{
    private static final String[] DOMINI = { "de", "it", "fr", "es", "nl", "at", "ch", "be", "co.uk" };
    private static final int[] POSIZIONI = { 0, 10, 25, 50, 100, 1000, 3200 };
    private static final String[] POSIZIONI_TESTO = { "lascia quelle già presenti", "10", "25", "50", "100",
                    "1000 (sconsigliato)", "tutte (sconsigliato)" };

    private Combo dominio;
    private Combo posizioni;
    private Button azioni;
    private Button avvia;
    private Text log;
    private Job job;

    @Override
    protected String getDefaultTitle()
    {
        return "Classificatore Morningstar";
    }

    @Override
    protected Control createBody(Composite parent)
    {
        var body = new Composite(parent, SWT.NONE);
        body.setBackground(Colors.theme().defaultBackground());
        GridLayoutFactory.fillDefaults().numColumns(2).margins(10, 10).spacing(10, 6).applyTo(body);

        var nota = new Label(body, SWT.WRAP);
        nota.setBackground(body.getBackground());
        nota.setForeground(Colors.theme().grayForeground());
        nota.setText("Legge da Morningstar la composizione dei tuoi ETF e fondi e aggiorna le tassonomie Asset Type, "
                        + "Stock Style, Stock Sector, Bond Style, Bond Sector, Region, Country e Holding. "
                        + "Servono l'ISIN dei titoli e la connessione a internet. Anche i fondi obbligazionari e "
                        + "\"Capital Preservation\" sono classificati, con le obbligazioni separate nei paesi "
                        + "(es. \"France (Bonds)\"). Nella nota di un titolo puoi scrivere #PPC:SKIP per saltarlo "
                        + "oppure #PPC:[ISIN2=...] per usare un ISIN alternativo. Dopo la classificazione salva il file.");
        GridDataFactory.fillDefaults().span(2, 1).grab(true, false).hint(400, SWT.DEFAULT).applyTo(nota);

        etichetta(body, "Sito Morningstar");
        dominio = new Combo(body, SWT.READ_ONLY);
        for (var d : DOMINI)
            dominio.add("morningstar." + d);
        dominio.select(0);

        etichetta(body, "Posizioni dei Fondi (Holding)");
        posizioni = new Combo(body, SWT.READ_ONLY);
        posizioni.setItems(POSIZIONI_TESTO);
        posizioni.select(4);

        new Label(body, SWT.NONE).setBackground(body.getBackground());
        azioni = new Button(body, SWT.CHECK);
        azioni.setText("Classifica anche le azioni singole");
        azioni.setBackground(body.getBackground());

        new Label(body, SWT.NONE).setBackground(body.getBackground());
        avvia = new Button(body, SWT.PUSH);
        avvia.setText("Classifica i Titoli");
        avvia.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> avvia()));

        var titoloLog = new Label(body, SWT.NONE);
        titoloLog.setText("Resoconto");
        titoloLog.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.HEADING2);
        titoloLog.setBackground(body.getBackground());
        GridDataFactory.fillDefaults().span(2, 1).indent(0, 10).applyTo(titoloLog);

        log = new Text(body, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        GridDataFactory.fillDefaults().span(2, 1).grab(true, true).hint(400, 300).applyTo(log);

        return body;
    }

    private void etichetta(Composite parent, String testo)
    {
        var label = new Label(parent, SWT.NONE);
        label.setText(testo);
        label.setBackground(parent.getBackground());
    }

    private void avvia()
    {
        if (job != null)
        {
            job.cancel();
            return;
        }

        var client = getClient();
        var fonte = new MorningstarWeb(DOMINI[dominio.getSelectionIndex()]);
        var opzioni = MorningstarDati.Opzioni.di(azioni.getSelection(), POSIZIONI[posizioni.getSelectionIndex()]);
        var display = Display.getCurrent();

        log.setText("Classificazione in corso...\n");
        avvia.setText("Interrompi");

        job = new Job("Classificatore Morningstar")
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                List<MorningstarClassificatore.Risultato> risultati = MorningstarClassificatore.leggi(client, fonte,
                                opzioni, msg -> scrivi(display, msg), monitor::isCanceled);

                display.asyncExec(() -> {
                    if (!monitor.isCanceled() && !risultati.isEmpty())
                    {
                        MorningstarClassificatore.applica(client, risultati, msg -> scrivi(display, msg));
                        client.markDirty();
                        scrivi(display, "\nFatto: " + risultati.size()
                                        + " titoli classificati. Ricordati di salvare il file.");
                    }
                    else if (risultati.isEmpty())
                    {
                        scrivi(display, "\nNessun titolo classificato: il file non è stato modificato.");
                    }
                    else
                    {
                        scrivi(display, "\nInterrotto: il file non è stato modificato.");
                    }
                    finito();
                });
                return Status.OK_STATUS;
            }
        };
        job.setUser(false);
        job.schedule();
    }

    private void finito()
    {
        job = null;
        if (!avvia.isDisposed())
            avvia.setText("Classifica i Titoli");
    }

    private void scrivi(Display display, String messaggio)
    {
        display.asyncExec(() -> {
            if (!log.isDisposed())
                log.append(messaggio + "\n");
        });
    }

    @Override
    public void dispose()
    {
        if (job != null)
            job.cancel();
        super.dispose();
    }
}
