package ee.forgr.capacitor_inappbrowser;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class ViewUtils {

  /**
   * Recursively goes through all views in the hierarchy and adds them to a list.
   * @param root The root view from where to start the search.
   * @return A list of all views within the hierarchy.
   */
  public static List<View> getAllViews(View root) {
    List<View> allViews = new ArrayList<>();
    if (root instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) root;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        View child = viewGroup.getChildAt(i);
        // Recursive call
        allViews.addAll(getAllViews(child));
      }
    } else {
      allViews.add(root);
    }
    return allViews;
  }
}
