// Decompile Auto enhance helpers (libcameraservice.so)
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class DecompileAutoEnhanceHelpers extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        String[] want = {
            "filter", "preprocess", "addDetailDelta", "appendDetailDelta", "setBuffer",
            "IPLFPhotoFixCreateLut", "IPLFPhotoFixInitLut", "IPLFPhotoFixStretchLut",
            "IPLFPhotoFixShrinkLut", "IPLFPhotoFixApplyLutOnHistogram",
            "IPLFPhotoFixFindFromBottom", "IPLFPhotoFixFindFromTop",
            "IPLFPhotoFixCreateHistogram", "IPLFPhotoFixExportLut",
            "IPLFExposure_SetParams", "IPLFBrightness_SetParams",
            "IPLFLocalIntensityBoost_SetParams", "IPLFPhotoFix_SetParams",
            "ctnoisereduction_create", "ctnoisereduction_setLevel",
            "ctnoisereduction_createNRImage", "ctnoisereduction_setYVU420SPBuffer"
        };
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        while (it.hasNext()) {
            Symbol s = it.next();
            String n = s.getName();
            boolean match = false;
            for (String w : want) if (n.equals(w) || n.contains(w)) { match = true; break; }
            if (!match) continue;
            Address a = s.getAddress();
            if (!seen.add(a.getOffset())) continue;
            Function f = getFunctionAt(a);
            if (f == null) continue;
            println("##### " + n + " @ " + a);
            DecompileResults res = di.decompileFunction(f, 180, monitor);
            if (res.decompileCompleted()) println(res.getDecompiledFunction().getC());
            else println("// failed: " + res.getErrorMessage());
        }
    }
}
