/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface App for Android.
 *
 * The Cyface App for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface App for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface App for Android. If not, see <http://www.gnu.org/licenses/>.
 */
/*package de.cyface.app.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.cyface.persistence.model.Measurement;

/**
 * Represents a storage of measurements in the local file system. You can get the data in this storage as a
 * <code>LiveData</code> object and observe changes to it, while respecting the Android lifecycle.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 * /
final class MeasurementsStorage {
    /**
     * A <code>LiveData</code> representation of the {@link Measurement} objects stored in this storage.
     * /
    private final MutableLiveData<List<Measurement>> data;
    /**
     * All the {@link Measurement} stored in this storage.
     * /
    private final List<Measurement> storedData;

    /**
     * Create a new empty but completely initialized storage for {@link Measurement} objects.
     * /
    MeasurementsStorage() {
        this.data = new MutableLiveData<>();
        this.storedData = new ArrayList<>();
    }

    /**
     * @return The data contained in this storage as an Android <code>LiveData</code> object.
     * /
    @NonNull
    LiveData<List<Measurement>> getData() {
        return data;
    }

    /**
     * Adds a measurement to this storage.
     *
     * @param file A directory representing a measurement on the local file system.
     * /
    void add(final @NonNull File file) {
        final Measurement measurement = new Measurement(Long.valueOf(file.getName()));
        storedData.add(measurement);
        data.postValue(storedData);
    }

    /**
     * Removes a measurement from this storage.
     *
     * @param file A directory representing a measurement on the local file system.
     * /
    void remove(final @NonNull File file) {
        final Measurement measurement = new Measurement(Long.valueOf(file.getName()));
        if (!storedData.remove(measurement)) {
            throw new IllegalStateException("Trying to remove measurement " + measurement
                    + " from non existing list. There should be no measurement there for removal to begin with.");
        }
        data.postValue(storedData);
    }
}
*/
