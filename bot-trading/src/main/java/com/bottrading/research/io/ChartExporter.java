package com.bottrading.research.io;

import com.bottrading.research.backtest.EquityPoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ChartExporter {

  private final CsvWriter csvWriter;

  public ChartExporter(CsvWriter csvWriter) {
    this.csvWriter = csvWriter;
  }

  public void export(Path directory, List<EquityPoint> equityCurve) throws IOException {
    if (equityCurve == null || equityCurve.isEmpty()) {
      return;
    }
    if (!Files.exists(directory)) {
      Files.createDirectories(directory);
    }
    List<String[]> equityRows = new ArrayList<>();
    equityRows.add(new String[] {"timestamp", "equity"});
    for (EquityPoint point : equityCurve) {
      equityRows.add(new String[] {String.valueOf(point.time().toEpochMilli()), point.equity().toPlainString()});
    }
    csvWriter.write(directory.resolve("equity.csv"), equityRows);

    List<String[]> drawdownRows = new ArrayList<>();
    drawdownRows.add(new String[] {"timestamp", "drawdown"});
    double peak = equityCurve.get(0).equity().doubleValue();
    for (EquityPoint point : equityCurve) {
      peak = Math.max(peak, point.equity().doubleValue());
      double dd = peak == 0 ? 0 : (peak - point.equity().doubleValue()) / peak;
      drawdownRows.add(new String[] {String.valueOf(point.time().toEpochMilli()), String.format("%.6f", dd)});
    }
    csvWriter.write(directory.resolve("drawdown.csv"), drawdownRows);

    Path script = directory.resolve("plot_equity.py");
    if (!Files.exists(script)) {
      Files.writeString(
          script,
          "import pandas as pd\nimport matplotlib.pyplot as plt\n"
              + "eq = pd.read_csv('equity.csv')\n"
              + "dd = pd.read_csv('drawdown.csv')\n"
              + "fig, ax = plt.subplots(2,1,sharex=True)\n"
              + "ax[0].plot(pd.to_datetime(eq['timestamp'], unit='ms'), eq['equity'])\n"
              + "ax[0].set_title('Equity Curve')\n"
              + "ax[1].plot(pd.to_datetime(dd['timestamp'], unit='ms'), dd['drawdown'])\n"
              + "ax[1].set_title('Drawdown')\n"
              + "plt.tight_layout()\nplt.show()\n");
    }
  }
}
