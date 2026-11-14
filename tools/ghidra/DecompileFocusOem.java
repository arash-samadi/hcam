// Decompile HTC/Qualcomm liboemcamera focus-related parameter handlers.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class DecompileFocusOem extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        String[] want = {
            "config_proc_CAMERA_SET_PARM_AF_ROI",
            "config_proc_CAMERA_SET_CAF",
            "config_proc_CAMERA_SET_PARM_AEC_ROI",
            "config_proc_CAMERA_SET_PARM_OLA_FD_STATE"
        };
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        while (it.hasNext()) {
            Symbol s = it.next();
            String n = s.getName();
            boolean match = false;
            for (String w : want) if (n.equals(w)) { match = true; break; }
            if (!match) continue;
            Address a = s.getAddress();
            if (!seen.add(a.getOffset())) continue;
            Function f = getFunctionAt(a);
            if (f == null) continue;
            println("##### " + n + " @ " + a);
            DecompileResults r = di.decompileFunction(f, 300, monitor);
            if (r.decompileCompleted()) println(r.getDecompiledFunction().getC());
        }
    }
}
