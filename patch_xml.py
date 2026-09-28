import re

with open("app/src/main/res/layout/activity_main.xml", "r") as f:
    xml = f.read()

toggle_xml = """
            <!-- Mode Selector -->
            <com.google.android.material.button.MaterialButtonToggleGroup
                android:id="@+id/toggleMode"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                app:singleSelection="true"
                app:selectionRequired="true"
                app:checkedButton="@+id/btnModeIdCard">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnModeIdCard"
                    style="?attr/materialButtonOutlinedStyle"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="ID Card" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnModeDocument"
                    style="?attr/materialButtonOutlinedStyle"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Full Document" />
            </com.google.android.material.button.MaterialButtonToggleGroup>

            <!-- ================= FRONT CARD SLOT ================= -->
"""

xml = xml.replace(
    '            <!-- ================= FRONT CARD SLOT ================= -->',
    toggle_xml.strip()
)

# Also add id to front title and back card
xml = xml.replace(
    'android:text="Front Side (Required)"',
    'android:id="@+id/tvFrontTitle"\n                        android:text="Front Side (Required)"'
)
xml = xml.replace(
    '<!-- ================= BACK CARD SLOT (OPTIONAL) ================= -->\n            <com.google.android.material.card.MaterialCardView',
    '<!-- ================= BACK CARD SLOT (OPTIONAL) ================= -->\n            <com.google.android.material.card.MaterialCardView\n                android:id="@+id/cardBackSlot"'
)

with open("app/src/main/res/layout/activity_main.xml", "w") as f:
    f.write(xml)
