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
package de.cyface.app.ui.button;

import com.github.lzyzsd.circleprogress.DonutProgress;

import android.view.View;
import android.widget.ImageButton;

/**
 * Interface for {@code onClickListener} for buttons as used by the Cyface main fragment.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
public interface AbstractButton extends View.OnClickListener {

    /**
     * This method should be called each time the view containing this button is destroyed. Usually this happens as part
     * of the {@code Fragment#onDestroyView()} method.
     */
    void onCreateView(ImageButton button, DonutProgress progress);

    /**
     * This method should be called each time the view containing this button is destroyed. Usually this happens as part
     * of the {@code Fragment#onDestroyView()} method.
     */
    void onDestroyView();

    void addButtonListener(ButtonListener buttonListener);
}
