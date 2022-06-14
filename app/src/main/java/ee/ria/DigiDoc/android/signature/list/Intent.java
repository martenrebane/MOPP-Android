package ee.ria.DigiDoc.android.signature.list;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.io.File;

import ee.ria.DigiDoc.android.utils.SivaUtil;
import ee.ria.DigiDoc.android.utils.files.FileStream;
import ee.ria.DigiDoc.android.utils.mvi.MviIntent;

interface Intent extends MviIntent {

    @AutoValue
    abstract class InitialIntent implements Intent {

        static InitialIntent create() {
            return new AutoValue_Intent_InitialIntent();
        }
    }

    @AutoValue
    abstract class UpButtonIntent implements Intent {

        static UpButtonIntent create() {
            return new AutoValue_Intent_UpButtonIntent();
        }
    }

    @AutoValue
    abstract class ContainerOpenIntent implements Intent, Action {

        @Nullable abstract File containerFile();

        abstract boolean confirmation();

        abstract boolean isSivaConfirmed();

        static ContainerOpenIntent confirmation(File containerFile) {
            boolean isConfirmationNeeded = SivaUtil.isSivaConfirmationNeeded(
                    ImmutableList.of(FileStream.create(containerFile)));
            return create(containerFile, isConfirmationNeeded, false);
        }

        static ContainerOpenIntent open(File containerFile, boolean isSivaConfirmed) {
            return create(containerFile, false, isSivaConfirmed);
        }

        static ContainerOpenIntent cancel() {
            return create(null, false, true);
        }

        private static ContainerOpenIntent create(@Nullable File containerFile, boolean confirmation, boolean isSivaConfirmed) {
            return new AutoValue_Intent_ContainerOpenIntent(containerFile, confirmation, isSivaConfirmed);
        }
    }

    @AutoValue
    abstract class ContainerRemoveIntent implements Intent {

        @Nullable abstract File containerFile();

        abstract boolean confirmation();

        static ContainerRemoveIntent confirmation(File containerFile) {
            return create(containerFile, true);
        }

        static ContainerRemoveIntent remove(File containerFile) {
            return create(containerFile, false);
        }

        static ContainerRemoveIntent cancel() {
            return create(null, false);
        }

        private static ContainerRemoveIntent create(@Nullable File containerFile,
                                                    boolean confirmation) {
            return new AutoValue_Intent_ContainerRemoveIntent(containerFile, confirmation);
        }
    }

    @AutoValue
    abstract class RefreshIntent implements Intent {

        static RefreshIntent create() {
            return new AutoValue_Intent_RefreshIntent();
        }
    }
}
