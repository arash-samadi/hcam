// Decompile the NR convolution FUN_a8017b7c and handleRawPicture parameter flow
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class DecompileConvolution extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        long base = currentProgram.getImageBase().getOffset();
        Function conv = getFunctionContaining(toAddr(base + 0x17b7cL));
        if (conv != null) {
            println("##### CONV " + conv.getName() + " @ " + conv.getEntryPoint());
            DecompileResults r = di.decompileFunction(conv, 240, monitor);
            if (r.decompileCompleted()) println(r.getDecompiledFunction().getC());
        }
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        while (it.hasNext()) {
            Symbol s = it.next();
            if (!s.getName().contains("handleRawPicture")) continue;
            Function f = getFunctionAt(s.getAddress());
            if (f == null) continue;
            println("##### " + s.getName() + " @ " + s.getAddress());
            DecompileResults r = di.decompileFunction(f, 240, monitor);
            if (r.decompileCompleted()) println(r.getDecompiledFunction().getC());
            break;
        }
    }
}
