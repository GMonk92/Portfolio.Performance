package name.abuchen.portfolio.ui.views.piano;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.piano.PianoInvestimento;
import name.abuchen.portfolio.piano.PianoRevisione;
import name.abuchen.portfolio.piano.PianoStore;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.Colors;

/**
 * Sezione "Revisione PAC": corrisponde ai fogli Portafoglio e Revisione del
 * file Excel. Quote e prezzi arrivano da Portfolio Performance; per ogni
 * strumento si sceglie l'asset class e il peso dentro l'asset class.
 */
@SuppressWarnings("nls")
public class RevisionePacView extends AbstractFinanceView
{
    @Inject
    private ExchangeRateProviderFactory factory;

    private PianoInvestimento piano;
    private TableViewer viewer;
    private Composite container;

    private Text annoText;
    private Label pacInCorso;
    private Label inflazione;
    private Label nuovoPac;
    private Label aumento;
    private Label totali;
    private Label avviso;

    @Override
    protected String getDefaultTitle()
    {
        return "Revisione PAC";
    }

    @Override
    protected Control createBody(Composite parent)
    {
        piano = PianoStore.load(getClient());

        container = new Composite(parent, SWT.NONE);
        container.setBackground(Colors.theme().defaultBackground());
        GridLayoutFactory.fillDefaults().applyTo(container);

        creaIntestazione(container);
        creaTabella(container);

        totali = new Label(container, SWT.NONE);
        totali.setBackground(container.getBackground());
        GridDataFactory.fillDefaults().grab(true, false).indent(5, 5).applyTo(totali);

        avviso = new Label(container, SWT.NONE);
        avviso.setForeground(Colors.theme().redForeground());
        avviso.setBackground(container.getBackground());
        GridDataFactory.fillDefaults().grab(true, false).indent(5, 0).applyTo(avviso);

        var nota = new Label(container, SWT.WRAP);
        nota.setText("Strumenti e controvalori arrivano da Portfolio Performance (titoli posseduti oggi): aggiorna "
                        + "i prezzi prima della revisione. In blu le celle da compilare: per ogni strumento scegli "
                        + "l'asset class e il peso dentro l'asset class (dentro ogni asset class i pesi devono fare "
                        + "100%). Il nuovo PAC va in proporzione a quanto manca a ogni strumento per tornare al peso "
                        + "target dopo 12 mesi di versamenti.");
        nota.setForeground(Colors.theme().grayForeground());
        nota.setBackground(container.getBackground());
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
        annoText.setText(String.valueOf(annoRevisione()));
        aggiorna();
    }

    private int annoRevisione()
    {
        return piano.getAnnoRevisione() > 0 ? piano.getAnnoRevisione() : LocalDate.now().getYear();
    }

    private void creaIntestazione(Composite parent)
    {
        var header = new Composite(parent, SWT.NONE);
        header.setBackground(parent.getBackground());
        GridLayoutFactory.fillDefaults().numColumns(2).margins(10, 10).spacing(10, 4).applyTo(header);

        etichetta(header, "Anno da Chiudere");
        annoText = new Text(header, SWT.BORDER | SWT.RIGHT);
        annoText.setForeground(Colors.BLUE);
        annoText.setText(String.valueOf(annoRevisione()));
        GridDataFactory.fillDefaults().hint(100, SWT.DEFAULT).applyTo(annoText);
        Runnable applica = () -> {
            try
            {
                var valore = PianoFormat.leggi(annoText.getText());
                if (valore != null && (int) Math.round(valore) != piano.getAnnoRevisione())
                {
                    piano.setAnnoRevisione((int) Math.round(valore));
                    PianoStore.save(getClient(), piano);
                }
            }
            catch (ParseException e)
            {
                // valore non valido: resta l'anno precedente
            }
            annoText.setText(String.valueOf(annoRevisione()));
            aggiorna();
        };
        annoText.addListener(SWT.DefaultSelection, e -> applica.run());
        annoText.addListener(SWT.FocusOut, e -> applica.run());

        etichetta(header, "PAC in Corso (€)");
        pacInCorso = valore(header, false);
        etichetta(header, "Inflazione dell'Anno (%)");
        inflazione = valore(header, false);
        etichetta(header, "Nuovo PAC da Gennaio (€)");
        nuovoPac = valore(header, true);
        etichetta(header, "Aumento Mensile (€)");
        aumento = valore(header, false);
    }

    private void etichetta(Composite parent, String testo)
    {
        var label = new Label(parent, SWT.NONE);
        label.setText(testo);
        label.setBackground(parent.getBackground());
    }

    private Label valore(Composite parent, boolean grassetto)
    {
        var label = new Label(parent, SWT.RIGHT);
        label.setBackground(parent.getBackground());
        if (grassetto)
            label.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.HEADING2);
        GridDataFactory.fillDefaults().hint(450, SWT.DEFAULT).applyTo(label);
        return label;
    }

    private void creaTabella(Composite parent)
    {
        var tableArea = new Composite(parent, SWT.NONE);
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

        colonna(layout, "Strumento", 250, SWT.LEFT, PianoRevisione.Riga::getNome, null, null);

        var classe = colonna(layout, "Asset Class", 120, SWT.LEFT,
                        r -> r.getAssetClass() == null ? "scegli\u2026" : r.getAssetClass(), null, null);
        classe.setEditingSupport(new AssetClassEditingSupport());

        var peso = colonna(layout, "Peso nella Classe", 120, SWT.RIGHT,
                        r -> r.getAssetClass() == null ? "" : PianoFormat.percentuale(r.getPesoNellaClasse()), null,
                        null);
        peso.setEditingSupport(new PesoEditingSupport());

        colonna(layout, "Controvalore", 110, SWT.RIGHT, r -> PianoFormat.euro(r.getControvalore()), null, null);
        colonna(layout, "Peso Attuale", 100, SWT.RIGHT, r -> PianoFormat.percentuale(r.getPesoAttuale()), null,
                        null);
        colonna(layout, "Peso Target", 100, SWT.RIGHT, r -> PianoFormat.percentuale(r.getPesoTarget()), null, null);
        colonna(layout, "Scostamento", 100, SWT.RIGHT, r -> PianoFormat.percentuale(r.getScostamento()),
                        this::coloreStato, null);
        colonna(layout, "Stato", 110, SWT.LEFT, r -> r.getStato().getLabel(), this::coloreStato, null);
        colonna(layout, "Nuovo PAC Mensile", 130, SWT.RIGHT, r -> PianoFormat.euro(r.getNuovoPac()), null,
                        r -> bold);
        colonna(layout, "Quota del PAC", 100, SWT.RIGHT, r -> PianoFormat.percentuale(r.getQuotaPac()), null, null);
    }

    private Color coloreStato(PianoRevisione.Riga riga)
    {
        return switch (riga.getStato())
        {
            case SOTTO_PESATO -> Colors.theme().redForeground();
            case SOVRA_PESATO -> Colors.theme().greenForeground();
            default -> null;
        };
    }

    private TableViewerColumn colonna(TableColumnLayout layout, String titolo, int larghezza, int allineamento,
                    Function<PianoRevisione.Riga, String> testo, Function<PianoRevisione.Riga, Color> colore,
                    Function<PianoRevisione.Riga, Font> font)
    {
        var column = new TableViewerColumn(viewer, allineamento);
        column.getColumn().setText(titolo);
        layout.setColumnData(column.getColumn(), new ColumnWeightData(larghezza, larghezza));

        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return testo.apply((PianoRevisione.Riga) element);
            }

            @Override
            public Color getForeground(Object element)
            {
                if (column.getColumn().getText().equals("Asset Class")
                                || column.getColumn().getText().equals("Peso nella Classe"))
                    return Colors.BLUE;
                return colore != null ? colore.apply((PianoRevisione.Riga) element) : null;
            }

            @Override
            public Font getFont(Object element)
            {
                return font != null ? font.apply((PianoRevisione.Riga) element) : null;
            }
        });
        return column;
    }

    private String[] nomiAssetClass()
    {
        List<String> nomi = new ArrayList<>();
        nomi.add("");
        piano.getAssetClass().stream().map(PianoInvestimento.AssetClass::getNome)
                        .filter(n -> n != null && !n.isBlank()).forEach(nomi::add);
        return nomi.toArray(new String[0]);
    }

    private final class AssetClassEditingSupport extends EditingSupport
    {
        private String[] items = new String[0];

        AssetClassEditingSupport()
        {
            super(viewer);
        }

        @Override
        protected CellEditor getCellEditor(Object element)
        {
            items = nomiAssetClass();
            return new ComboBoxCellEditor(viewer.getTable(), items, SWT.READ_ONLY);
        }

        @Override
        protected boolean canEdit(Object element)
        {
            return true;
        }

        @Override
        protected Object getValue(Object element)
        {
            var nome = ((PianoRevisione.Riga) element).getAssetClass();
            for (int ii = 0; ii < items.length; ii++)
                if (items[ii].equals(nome))
                    return ii;
            return 0;
        }

        @Override
        protected void setValue(Object element, Object value)
        {
            int index = (Integer) value;
            if (index < 0 || index >= items.length)
                return;
            var riga = (PianoRevisione.Riga) element;
            var strumento = piano.getOrCreateStrumento(riga.getUuid());
            var nome = items[index].isEmpty() ? null : items[index];
            if (nome == null ? strumento.getAssetClass() == null : nome.equals(strumento.getAssetClass()))
                return;
            strumento.setAssetClass(nome);
            if (nome != null && strumento.getPesoNellaClasse() == 0)
                strumento.setPesoNellaClasse(1);
            salvaEAggiorna();
        }
    }

    private final class PesoEditingSupport extends EditingSupport
    {
        PesoEditingSupport()
        {
            super(viewer);
        }

        @Override
        protected CellEditor getCellEditor(Object element)
        {
            return new TextCellEditor(viewer.getTable());
        }

        @Override
        protected boolean canEdit(Object element)
        {
            return ((PianoRevisione.Riga) element).getAssetClass() != null;
        }

        @Override
        protected Object getValue(Object element)
        {
            return PianoFormat.percentualeModifica(((PianoRevisione.Riga) element).getPesoNellaClasse());
        }

        @Override
        protected void setValue(Object element, Object value)
        {
            var riga = (PianoRevisione.Riga) element;
            try
            {
                var peso = PianoFormat.leggiPercentuale(String.valueOf(value));
                var nuovo = peso == null ? 0 : peso;
                if (Math.abs(nuovo - riga.getPesoNellaClasse()) < 0.0000001)
                    return;
                piano.getOrCreateStrumento(riga.getUuid()).setPesoNellaClasse(nuovo);
                salvaEAggiorna();
            }
            catch (ParseException e)
            {
                // valore non valido: resta il valore precedente
            }
        }
    }

    private List<PianoRevisione.Input> strumentiDaPP()
    {
        var oggi = LocalDate.now();
        var converter = new CurrencyConverterImpl(factory, getClient().getBaseCurrency());
        var snapshot = ClientSnapshot.create(getClient(), converter, oggi);

        List<PianoRevisione.Input> input = new ArrayList<>();
        var visti = new HashSet<String>();

        for (var position : snapshot.getJointPortfolio().getPositions())
        {
            if (position.getShares() <= 0)
                continue;
            var security = position.getSecurity();
            var valore = position.calculateValue().with(converter.at(oggi));
            input.add(new PianoRevisione.Input(security.getUUID(), security.getName(),
                            PianoFormat.fromMoney(valore)));
            visti.add(security.getUUID());
        }

        // strumenti già assegnati a una asset class ma non più posseduti
        for (var security : getClient().getSecurities())
        {
            if (visti.contains(security.getUUID()))
                continue;
            var assegnato = piano.getStrumento(security.getUUID())
                            .map(s -> s.getAssetClass() != null && s.getPesoNellaClasse() > 0).orElse(false);
            if (assegnato)
                input.add(new PianoRevisione.Input(security.getUUID(), security.getName(), 0));
        }

        input.sort(Comparator.comparing(PianoRevisione.Input::nome, String.CASE_INSENSITIVE_ORDER));
        return input;
    }

    private void salvaEAggiorna()
    {
        PianoStore.save(getClient(), piano);
        aggiorna();
    }

    private void aggiorna()
    {
        var revisione = PianoRevisione.calcola(piano, annoRevisione(), strumentiDaPP());

        pacInCorso.setText(PianoFormat.euro(revisione.getPacInCorso()));
        inflazione.setText(revisione.getInflazione() != null ? PianoFormat.percentuale2(revisione.getInflazione())
                        : "inserisci prima l'inflazione dell'anno nel Prospetto");
        inflazione.setForeground(revisione.getInflazione() != null ? Colors.theme().defaultForeground()
                        : Colors.theme().redForeground());
        nuovoPac.setText(PianoFormat.euro(revisione.getNuovoPac()));
        aumento.setText(revisione.getNuovoPac() != null
                        ? PianoFormat.euro(revisione.getNuovoPac() - revisione.getPacInCorso())
                        : "");

        viewer.setInput(revisione.getRighe());
        viewer.refresh();

        var sommaPac = revisione.getRighe().stream().map(PianoRevisione.Riga::getNuovoPac)
                        .filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        totali.setText("Totale: controvalore " + PianoFormat.euro(revisione.getTotaleControvalore())
                        + "   ·   peso target " + PianoFormat.percentuale(revisione.getTotalePesoTarget())
                        + (revisione.getNuovoPac() != null ? "   ·   nuovo PAC " + PianoFormat.euro(sommaPac) : ""));

        String messaggio = "";
        if (Math.abs(piano.getTotaleAssetClass() - 1) > 0.00005)
            messaggio = "Le asset class in Parametri non fanno 100%.";
        else if (Math.abs(revisione.getTotalePesoTarget() - 1) > 0.00005)
            messaggio = "I pesi target degli strumenti non fanno 100%: controlla asset class e pesi nella classe.";
        avviso.setText(messaggio);

        container.layout(true, true);
    }
}
