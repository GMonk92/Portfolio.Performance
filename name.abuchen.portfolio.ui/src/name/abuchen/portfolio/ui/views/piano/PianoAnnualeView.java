package name.abuchen.portfolio.ui.views.piano;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.piano.PianoCalcolo;
import name.abuchen.portfolio.piano.PianoInvestimento;
import name.abuchen.portfolio.piano.PianoStore;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.Colors;

/**
 * Sezione "Piano anno per anno": corrisponde al foglio Piano del file Excel.
 * Si compilano l'inflazione ISTAT e il valore del portafoglio al 31/12; gli
 * altri valori dell'anno compaiono solo dopo aver inserito l'inflazione.
 */
@SuppressWarnings("nls")
public class PianoAnnualeView extends AbstractFinanceView
{
    @FunctionalInterface
    private interface Setter
    {
        void set(int anno, String text) throws ParseException;
    }

    @Inject
    private ExchangeRateProviderFactory factory;

    private PianoInvestimento piano;
    private TableViewer viewer;

    @Override
    protected String getDefaultTitle()
    {
        return "Piano anno per anno";
    }

    @Override
    protected Control createBody(Composite parent)
    {
        piano = PianoStore.load(getClient());

        var container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().applyTo(container);

        var tableArea = new Composite(container, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(tableArea);
        var layout = new TableColumnLayout();
        tableArea.setLayout(layout);

        viewer = new TableViewer(tableArea, SWT.FULL_SELECTION | SWT.BORDER);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        var activation = new ColumnViewerEditorActivationStrategy(viewer);
        TableViewerEditor.create(viewer, activation, ColumnViewerEditor.TABBING_HORIZONTAL
                        | ColumnViewerEditor.TABBING_MOVE_TO_ROW_NEIGHBOR | ColumnViewerEditor.KEYBOARD_ACTIVATION);

        var bold = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);

        colonna(layout, "Anno", 60, SWT.LEFT, r -> String.valueOf(r.getAnno()), r -> null, null, null);

        colonna(layout, "Inflazione ISTAT", 110, SWT.RIGHT, r -> PianoFormat.percentuale2(r.getInflazione()),
                        r -> null, null, new Modifica(r -> PianoFormat.percentualeModifica(r.getInflazione()),
                                        (anno, t) -> piano.setInflazione(anno, PianoFormat.leggiPercentuale(t))));

        colonna(layout, "PAC mensile", 110, SWT.RIGHT, r -> PianoFormat.euro(r.getPacMensile()), r -> null, null,
                        null);

        colonna(layout, "Target al 31/12 (nominale)", 160, SWT.RIGHT, r -> PianoFormat.euro(r.getTargetNominale()),
                        r -> null, null, null);

        colonna(layout, "Valore al 31/12", 140, SWT.RIGHT, r -> PianoFormat.euro(r.getValore()), r -> null, null,
                        new Modifica(r -> PianoFormat.numero(r.getValore()),
                                        (anno, t) -> piano.setValore(anno, PianoFormat.leggi(t))));

        colonna(layout, "Valore reale", 130, SWT.RIGHT, r -> PianoFormat.euro(r.getValoreReale()),
                        r -> r.isSottoTraiettoria() ? Colors.theme().redForeground() : null, null, null);

        colonna(layout, "Integrazione da versare", 160, SWT.RIGHT, r -> PianoFormat.euro(r.getIntegrazione()),
                        r -> r.getIntegrazione() != null && r.getIntegrazione() > 0 ? Colors.theme().redForeground()
                                        : null,
                        r -> r.getIntegrazione() != null && r.getIntegrazione() > 0 ? bold : null, null);

        hookContextMenu(viewer.getTable(), manager -> {
            var selection = (IStructuredSelection) viewer.getSelection();
            if (!(selection.getFirstElement() instanceof PianoCalcolo.Riga riga))
                return;

            var data = LocalDate.of(riga.getAnno(), 12, 31);
            if (data.isAfter(LocalDate.now()))
                data = LocalDate.now();
            var dataFinale = data;

            manager.add(new Action("Prendi il valore da Portfolio Performance (" + dataFinale + ")")
            {
                @Override
                public void run()
                {
                    piano.setValore(riga.getAnno(), valoreTitoli(dataFinale));
                    salvaEAggiorna();
                }
            });

            manager.add(new Action("Cancella i dati dell'anno " + riga.getAnno())
            {
                @Override
                public void run()
                {
                    piano.setInflazione(riga.getAnno(), null);
                    piano.setValore(riga.getAnno(), null);
                    salvaEAggiorna();
                }
            });
        });

        var nota = new Label(container, SWT.WRAP);
        nota.setText("In blu le celle da compilare: fai clic per inserire l'inflazione ISTAT e il valore del "
                        + "portafoglio al 31/12. I valori di ogni anno compaiono dopo che hai inserito l'inflazione "
                        + "(il PAC dell'anno dopo quella dell'anno prima). Valore reale in rosso: sotto la traiettoria. "
                        + "Con il tasto destro puoi prendere il valore al 31/12 direttamente da Portfolio Performance.");
        nota.setForeground(Colors.theme().grayForeground());
        GridDataFactory.fillDefaults().grab(true, false).hint(400, SWT.DEFAULT).indent(5, 5).applyTo(nota);

        aggiorna();

        return container;
    }

    @Override
    public void notifyModelUpdated()
    {
        if (viewer == null || viewer.getTable().isDisposed())
            return;
        piano = PianoStore.load(getClient());
        aggiorna();
    }

    private record Modifica(Function<PianoCalcolo.Riga, String> getter, Setter setter)
    {
    }

    private void colonna(TableColumnLayout layout, String titolo, int larghezza, int allineamento,
                    Function<PianoCalcolo.Riga, String> testo, Function<PianoCalcolo.Riga, Color> colore,
                    Function<PianoCalcolo.Riga, Font> font, Modifica modifica)
    {
        var column = new TableViewerColumn(viewer, allineamento);
        column.getColumn().setText(titolo);
        layout.setColumnData(column.getColumn(), new ColumnWeightData(larghezza, larghezza));

        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return testo.apply((PianoCalcolo.Riga) element);
            }

            @Override
            public Color getForeground(Object element)
            {
                if (modifica != null)
                    return Colors.BLUE;
                return colore.apply((PianoCalcolo.Riga) element);
            }

            @Override
            public Font getFont(Object element)
            {
                return font != null ? font.apply((PianoCalcolo.Riga) element) : null;
            }
        });

        if (modifica == null)
            return;

        column.setEditingSupport(new EditingSupport(viewer)
        {
            @Override
            protected CellEditor getCellEditor(Object element)
            {
                return new TextCellEditor(viewer.getTable());
            }

            @Override
            protected boolean canEdit(Object element)
            {
                return true;
            }

            @Override
            protected Object getValue(Object element)
            {
                return modifica.getter().apply((PianoCalcolo.Riga) element);
            }

            @Override
            protected void setValue(Object element, Object value)
            {
                var riga = (PianoCalcolo.Riga) element;
                var prima = modifica.getter().apply(riga);
                if (prima.equals(value))
                    return;

                try
                {
                    modifica.setter().set(riga.getAnno(), String.valueOf(value));
                    salvaEAggiorna();
                }
                catch (ParseException | IllegalArgumentException e)
                {
                    // valore non valido: resta il valore precedente
                }
            }
        });
    }

    private double valoreTitoli(LocalDate date)
    {
        var converter = new CurrencyConverterImpl(factory, getClient().getBaseCurrency());
        var snapshot = ClientSnapshot.create(getClient(), converter, date);
        return PianoFormat.fromMoney(snapshot.getJointPortfolio().getValue());
    }

    private void salvaEAggiorna()
    {
        PianoStore.save(getClient(), piano);
        aggiorna();
    }

    private void aggiorna()
    {
        var calcolo = PianoCalcolo.calcola(piano);
        viewer.setInput(calcolo.getRighe());
        viewer.refresh();
    }
}
