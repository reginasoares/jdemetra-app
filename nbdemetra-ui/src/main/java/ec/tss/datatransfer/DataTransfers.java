/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ec.tss.datatransfer;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import ec.tss.tsproviders.utils.Formatters;
import ec.tss.tsproviders.utils.Parsers;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.openide.util.datatransfer.ExTransferable;
import org.openide.util.datatransfer.MultiTransferObject;

/**
 *
 * @author Philippe Charles
 */
public final class DataTransfers {

    private DataTransfers() {
        // static class
    }

    public static DataFlavor newLocalObjectDataFlavor(Class<?> clazz) {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + clazz.getName());
        } catch (ClassNotFoundException ex) {
            throw Throwables.propagate(ex);
        }
    }

    public static Optional<File> getSingleFile(Transferable t) {
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (files.size() == 1) {
                    return Optional.of(files.get(0));
                }
            } catch (UnsupportedFlavorException | IOException ex) {
                throw Throwables.propagate(ex);
            }
        }
        return Optional.absent();
    }

    public static <T> Optional<T> tryParse(Transferable t, Parsers.Parser<T> parser) {
        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                return text != null ? parser.tryParse(text) : Optional.<T>absent();
            } catch (UnsupportedFlavorException ex) {
                throw Throwables.propagate(ex);
            } catch (IOException ex) {
                return Optional.absent();
            }
        }
        return Optional.absent();
    }

    public static <T> Optional<Transferable> tryFormat(T value, Formatters.Formatter<T> formatter) {
        String text = formatter.formatAsString(value);
        return text != null
                ? Optional.<Transferable>of(new StringSelection(text))
                : Optional.<Transferable>absent();
    }

    public static boolean isMultiFlavor(@Nonnull DataFlavor[] dataFlavors) {
        return dataFlavors.length == 1 && dataFlavors[0] == ExTransferable.multiFlavor;
    }

    @Nonnull
    public static java.util.Optional<MultiTransferObject> getMultiTransferObject(@Nonnull Transferable t) {
        if (isMultiFlavor(t.getTransferDataFlavors())) {
            try {
                return java.util.Optional.of((MultiTransferObject) t.getTransferData(ExTransferable.multiFlavor));
            } catch (UnsupportedFlavorException | IOException ex) {
                throw Throwables.propagate(ex);
            }
        }
        return java.util.Optional.empty();
    }

    @Nonnull
    public static Stream<Transferable> asTransferableStream(@Nonnull MultiTransferObject multi) {
        return IntStream.range(0, multi.getCount()).mapToObj(multi::getTransferableAt);
    }
}
