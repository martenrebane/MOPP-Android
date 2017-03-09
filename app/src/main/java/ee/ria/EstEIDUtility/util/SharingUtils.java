/*
 * Copyright 2017 Riigi Infosüsteemide Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package ee.ria.EstEIDUtility.util;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.EstEIDUtility.util.Constants.APP_PACKAGE_NAME;

public class SharingUtils {

    public static List<Intent> createTargetedSendIntentsForResolvers(List<ResolveInfo> availableResolvers, Uri uriToFile) {
        List<Intent> targetedIntents = new ArrayList<>();
        for (ResolveInfo resolveInfo : availableResolvers) {
            String packageName = resolvePackageName(resolveInfo);
            if (!APP_PACKAGE_NAME.equals(packageName)) {
                targetedIntents.add(createTargetedSendContainerIntent(uriToFile, packageName));
            }
        }
        return targetedIntents;
    }

    public static Map<String, Intent> createdTargetedViewIntentsForResolvers(List<ResolveInfo> availableResolvers, Uri contentUri, String mediaType) {
        Map<String, Intent> targetedIntents = new HashMap<>();
        for (ResolveInfo resolveInfo : availableResolvers) {
            String packageName = resolvePackageName(resolveInfo);
            if (!APP_PACKAGE_NAME.equals(packageName)) {
                targetedIntents.put(packageName, createTargetedViewIntent(contentUri, mediaType, packageName));
            }
        }
        return targetedIntents;
    }

    public static String resolvePackageName(ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) {
            return resolveInfo.activityInfo.packageName;
        } else if (resolveInfo.providerInfo != null) {
            return resolveInfo.providerInfo.packageName;
        } else {
            return resolveInfo.serviceInfo.packageName;
        }
    }

    public static Intent createContainerSendIntent(Uri containerUri) {
        return new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, containerUri)
                .setType("application/zip");
    }

    public static Intent createViewIntent(Uri contentUri, String mediaType) {
        return new Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri, mediaType)
            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    public static Intent createChooser(List<Intent> targetedIntents, CharSequence text) {
        return Intent
                .createChooser(targetedIntents.remove(0), text)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedIntents.toArray(new Parcelable[] {}));
    }

    private static Intent createTargetedViewIntent(Uri contentUri, String mediaType, String packageName) {
        return createViewIntent(contentUri, mediaType).setPackage(packageName);
    }

    private static Intent createTargetedSendContainerIntent(Uri uriToFile, String packageName) {
        return createContainerSendIntent(uriToFile).setPackage(packageName);
    }
}
