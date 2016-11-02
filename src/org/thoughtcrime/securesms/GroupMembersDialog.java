package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, Recipients> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipients recipients;
  private final Context    context;

  public GroupMembersDialog(Context context, Recipients recipients) {
    this.recipients = recipients;
    this.context    = context;
  }

  @Override
  public void onPreExecute() {}

  @Override
  protected Recipients doInBackground(Void... params) {
    try {
      String groupId = recipients.getPrimaryRecipient().getNumber();
      return DatabaseFactory.getGroupDatabase(context)
                            .getGroupMembers(GroupUtil.getDecodedId(groupId), true);
    } catch (IOException e) {
      Log.w(TAG, e);
      return RecipientFactory.getRecipientsFor(context, new LinkedList<Recipient>(), true);
    }
  }

  @Override
  public void onPostExecute(Recipients members) {
    final GroupMembersAdapter adapter = new GroupMembersAdapter(context, R.layout.group_members_dialog_item,
            R.id.member_name, members.getRecipientsList());
    final GroupMembersOnClickListener onClickListener = new GroupMembersOnClickListener(adapter);

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
    builder.setAdapter(adapter, onClickListener);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    if (recipients.isGroupRecipient()) execute();
    else                               onPostExecute(recipients);
  }

  private static class GroupMembersOnClickListener implements DialogInterface.OnClickListener {
    private final GroupMembersAdapter adapter;

    public GroupMembersOnClickListener(final GroupMembersAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int item) {
      final Context context = adapter.getContext(); // TODO: adapter context okay?
      final Recipient recipient = adapter.getItem(item);

      if (recipient.getContactUri() != null) {
        ContactsContract.QuickContact.showQuickContact(context, new Rect(0,0,0,0),
                                                       recipient.getContactUri(),
                                                       ContactsContract.QuickContact.MODE_LARGE, null);
      } else {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        context.startActivity(intent);
      }
    }
  }

  private static class GroupMembersAdapter extends ArrayAdapter<Recipient> {
    private final String TAG = GroupMembersAdapter.class.getSimpleName();

    private final LinkedList<Recipient> members;

    public GroupMembersAdapter(Context context, int layoutRes, int textViewResId, List<Recipient> recipients) {
      super(context, layoutRes, textViewResId, recipients);
      members = sortGroupMembers(recipients);
    }

    private LinkedList<Recipient> sortGroupMembers(final List<Recipient> recipients) {
      final LinkedList<Recipient> members = new LinkedList<>();
      for (final Recipient recipient : recipients) {
        if (isLocalNumber(recipient)) {
          members.push(recipient);
        } else {
          members.add(recipient);
        }
      }
      return members;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      // Recycles view & sets TextView to our item's getString (which we don't want).
      final View view = super.getView(position, convertView, parent);
      final TextView memberNameView = ViewUtil.findById(view, R.id.member_name);
      final ImageView memberPhotoView = ViewUtil.findById(view, R.id.member_photo);

      final Recipient member = getItem(position);
      memberNameView.setText(getMemberName(member));
      memberPhotoView.setImageDrawable(member.getContactPhoto().asDrawable(getContext(), 0)); // tODO: null photo? no photo?

      return view;
    }

    private String getMemberName(final Recipient member) {
      if (isLocalNumber(member)) {
        return getContext().getString(R.string.GroupMembersDialog_me);
      } else {
        return member.toShortString();
      }
    }

    private boolean isLocalNumber(Recipient recipient) {
      final Context context = getContext();
      try {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        String e164Number  = Util.canonicalizeNumber(context, recipient.getNumber());

        return e164Number != null && e164Number.equals(localNumber);
      } catch (InvalidNumberException e) {
        Log.w(TAG, e);
        return false;
      }
    }
  }
}
