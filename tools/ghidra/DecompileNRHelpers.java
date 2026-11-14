// Decompile the NR box-filter helpers and level/create internals
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class DecompileNRHelpers extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        long base = currentProgram.getImageBase().getOffset();
        long[] addrs = {0x16cf8L, 0x173a8L, 0x174ccL};
        for (long off : addrs) {
            Function f = getFunctionContaining(toAddr(base + off));
            if (f == null) continue;
            println("##### BOX " + f.getName() + " @ " + f.getEntryPoint());
            DecompileResults r = di.decompileFunction(f, 240, monitor);
            if (r.decompileCompleted()) println(r.getDecompiledFunction().getC());
        }
        String[] want = {"addDetailDelta", "ctnoisereduction_setLevel", "ctnoisereduction_create"};
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
