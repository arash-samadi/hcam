// Decompile ISP colour-science functions from liboemcamera.so
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class DecompileColorScience extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        String[] want = {
            "sensor_convert_chromatix_color_correction",
            "sensor_convert_chromatix_color_conversion",
            "vfe_util_update_color_conversion",
            "vfe_util_update_color_correction",
            "vfe_util_update_color_conversion_matrix",
            "effects_select_gamma_table",
            "effects_set_saturation",
            "effects_set_contrast",
            "awb_recalculate_by_HTC",
            "awb_init"
        };
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        while (it.hasNext()) {
            Symbol s = it.next();
            String n = s.getName();
            boolean match = false;
            for (String w : want) if (n.contains(w)) { match = true; break; }
            if (!match) continue;
            Address a = s.getAddress();
            if (!seen.add(a.getOffset())) continue;
            Function f = getFunctionAt(a);
            if (f == null) continue;
            println("##### " + n + " @ " + a);
            DecompileResults r = di.decompileFunction(f, 240, monitor);
            if (r.decompileCompleted()) println(r.getDecompiledFunction().getC());
        }
    }
}
