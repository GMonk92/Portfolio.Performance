package name.abuchen.portfolio.piano;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import name.abuchen.portfolio.model.Client;

/**
 * I valori attesi sono stati calcolati con il file Excel Piano_Investimenti.xlsx
 * usando gli stessi dati.
 */
@SuppressWarnings("nls")
public class PianoCalcoloTest
{
    private static final double DELTA = 0.01;

    @Test
    public void testValoriPredefiniti()
    {
        var piano = new PianoInvestimento();

        assertThat(piano.getQuotaAzionaria(), closeTo(0.7325, 0.00001));
        assertThat(piano.getAssetClass().get(0).getNome(), is("Equity"));
        assertThat(piano.getAssetClass().get(1).getPeso(), closeTo(0.2175, 0.00001));
        assertThat(piano.getTotaleAssetClass(), closeTo(1, 0.00001));

        var calcolo = PianoCalcolo.calcola(piano);

        assertThat(calcolo.getAnnoRaggiungimento(), is(2064));
        assertThat(calcolo.getPacNecessario(), closeTo(1882.83, DELTA));
        assertThat(calcolo.getRighe().size(), is(PianoInvestimento.NUMERO_ANNI + 1));

        var anno0 = calcolo.getRighe().get(0);
        assertThat(anno0.getAnno(), is(2026));
        assertThat(anno0.getPacMensile(), closeTo(800, DELTA));
        assertThat(anno0.getTargetNominale(), closeTo(35000, DELTA));
        assertThat(anno0.getValoreReale(), is(nullValue()));
        assertThat(anno0.getIntegrazione(), is(nullValue()));

        // senza inflazione inserita i valori degli anni successivi non compaiono
        var anno1 = calcolo.getRighe().get(1);
        assertThat(anno1.getPacMensile(), is(nullValue()));
        assertThat(anno1.getTargetNominale(), is(nullValue()));
    }

    @Test
    public void testAnniCompilati()
    {
        var piano = new PianoInvestimento();
        piano.setInflazione(2026, 0.019);
        piano.setValore(2026, 36000d);
        piano.setInflazione(2027, 0.025);
        piano.setValore(2027, 48000d);
        piano.setInflazione(2028, 0.03);
        piano.setValore(2028, 70000d);

        var calcolo = PianoCalcolo.calcola(piano);
        var righe = calcolo.getRighe();

        assertThat(righe.get(0).getValoreReale(), closeTo(36000, DELTA));
        assertThat(righe.get(0).getIntegrazione(), closeTo(0, DELTA));

        assertThat(righe.get(1).getPacMensile(), closeTo(815.20, DELTA));
        assertThat(righe.get(1).getTargetNominale(), closeTo(47164.15, DELTA));
        assertThat(righe.get(1).getValoreReale(), closeTo(46829.27, DELTA));
        assertThat(righe.get(1).getIntegrazione(), closeTo(0, DELTA));

        assertThat(righe.get(2).getPacMensile(), closeTo(835.58, DELTA));
        assertThat(righe.get(2).getTargetNominale(), closeTo(60646.36, DELTA));
        assertThat(righe.get(2).getValoreReale(), closeTo(66303.58, DELTA));

        // il PAC dell'anno dopo compare quando c'è l'inflazione dell'anno prima
        assertThat(righe.get(3).getPacMensile(), closeTo(860.65, DELTA));
        assertThat(righe.get(3).getTargetNominale(), is(nullValue()));
        assertThat(righe.get(4).getPacMensile(), is(nullValue()));

        assertThat(calcolo.getAnnoRaggiungimento(), is(2063));
    }

    @Test
    public void testIntegrazione()
    {
        var piano = new PianoInvestimento();
        piano.setInflazione(2026, 0.019);
        piano.setInflazione(2027, 0.025);
        piano.setValore(2027, 45000d);

        var riga = PianoCalcolo.calcola(piano).getRighe().get(1);
        assertThat(riga.getIntegrazione(), closeTo(47164.15 - 45000, DELTA));
        assertThat(riga.isSottoTraiettoria(), is(true));

        // il tetto limita l'integrazione
        piano.setValore(2027, 40000d);
        riga = PianoCalcolo.calcola(piano).getRighe().get(1);
        assertThat(riga.getIntegrazione(), closeTo(4000, DELTA));

        // sopra la soglia si ribilancia internamente
        piano.setSogliaRibilanciamento(30000);
        riga = PianoCalcolo.calcola(piano).getRighe().get(1);
        assertThat(riga.getIntegrazione(), closeTo(0, DELTA));
    }

    @Test
    public void testSalvataggioNelClient()
    {
        var client = new Client();
        var piano = PianoStore.load(client);
        piano.setEta(40);
        piano.setInflazione(2026, 0.019);
        piano.setValore(2026, 36000d);
        PianoStore.save(client, piano);

        var letto = PianoStore.load(client);
        assertThat(letto.getEta(), is(40));
        assertThat(letto.getInflazione(2026), closeTo(0.019, 0.000001));
        assertThat(letto.getValore(2026), closeTo(36000, DELTA));
        assertThat(letto.getValore(2027), is(nullValue()));

        // cancellando i dati l'anno sparisce
        letto.setInflazione(2026, null);
        letto.setValore(2026, null);
        assertThat(letto.getAnno(2026).isPresent(), is(false));
    }

    @Test
    public void testFilePrimaVersione()
    {
        var client = new Client();
        client.setProperty(PianoStore.PROPERTY_KEY, "{\"eta\":40,\"tassoBce\":2.0,\"quotaCommodities\":0.1,\"quotaCash\":0.05}");

        var piano = PianoStore.load(client);
        var classi = piano.getAssetClass();
        assertThat(classi.size(), is(PianoInvestimento.NUMERO_ASSET_CLASS));
        assertThat(classi.get(0).getPeso(), closeTo(0.75, 0.00001));
        assertThat(classi.get(1).getPeso(), closeTo(0.10, 0.00001));
        assertThat(classi.get(2).getPeso(), closeTo(0.10, 0.00001));
        assertThat(classi.get(3).getPeso(), closeTo(0.05, 0.00001));
        assertThat(classi.get(4).getNome(), is(""));

        // le asset class ora sono libere: cambiare l'età non le modifica
        piano.setEta(50);
        assertThat(piano.getAssetClass().get(0).getPeso(), closeTo(0.75, 0.00001));
    }

    @Test
    public void testRevisione()
    {
        var piano = new PianoInvestimento();
        var classi = piano.getAssetClass();
        classi.get(0).setPeso(0.70);
        classi.get(1).setPeso(0.25);
        classi.get(2).setPeso(0.05);
        classi.get(3).setPeso(0);

        piano.getOrCreateStrumento("a").setAssetClass("Equity");
        piano.getOrCreateStrumento("a").setPesoNellaClasse(1);
        piano.getOrCreateStrumento("b").setAssetClass("Bond");
        piano.getOrCreateStrumento("b").setPesoNellaClasse(1);
        piano.getOrCreateStrumento("c").setAssetClass("Commodities");
        piano.getOrCreateStrumento("c").setPesoNellaClasse(1);

        var input = java.util.List.of(new PianoRevisione.Input("a", "Azioni", 30000),
                        new PianoRevisione.Input("b", "Bond", 5000), new PianoRevisione.Input("c", "Oro", 0));

        // senza inflazione dell'anno il nuovo PAC non è noto
        var revisione = PianoRevisione.calcola(piano, 2026, input);
        assertThat(revisione.getNuovoPac(), is(nullValue()));
        assertThat(revisione.getPacInCorso(), closeTo(800, DELTA));
        assertThat(revisione.getRighe().get(0).getStato(), is(PianoRevisione.Stato.SOVRA_PESATO));
        assertThat(revisione.getRighe().get(1).getStato(), is(PianoRevisione.Stato.SOTTO_PESATO));

        piano.setInflazione(2026, 0.02);
        revisione = PianoRevisione.calcola(piano, 2026, input);
        assertThat(revisione.getNuovoPac(), closeTo(816, DELTA));

        // T = 35000 + 816*12 = 44792; mancanti: a = 31354,4-30000 = 1354,4;
        // b = 11198-5000 = 6198; c = 2239,6
        var righe = revisione.getRighe();
        double somma = 1354.4 + 6198 + 2239.6;
        assertThat(righe.get(0).getNuovoPac(), closeTo(816 * 1354.4 / somma, DELTA));
        assertThat(righe.get(1).getNuovoPac(), closeTo(816 * 6198 / somma, DELTA));
        assertThat(righe.get(2).getNuovoPac(), closeTo(816 * 2239.6 / somma, DELTA));
        assertThat(revisione.getTotalePesoTarget(), closeTo(1, 0.00001));
    }
}
