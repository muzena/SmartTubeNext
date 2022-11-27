package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import io.reactivex.Observable;

import java.util.List;

public abstract class BaseMenuPresenter extends BasePresenter<Void> {
    private final MediaServiceManager mServiceManager;
    private boolean mIsPinToSidebarEnabled;
    private boolean mIsSavePlaylistEnabled;
    private boolean mIsCreatePlaylistEnabled;
    private boolean mIsAccountSelectionEnabled;
    private boolean mIsAddToNewPlaylistEnabled;
    private boolean mIsToggleHistoryEnabled;
    private boolean mIsClearHistoryEnabled;
    private boolean mIsUpdateCheckEnabled;

    protected BaseMenuPresenter(Context context) {
        super(context);
        mServiceManager = MediaServiceManager.instance();
    }

    protected abstract Video getVideo();
    protected BrowseSection getSection() { return null; }
    protected abstract AppDialogPresenter getDialogPresenter();
    protected abstract VideoMenuCallback getCallback();

    public void closeDialog() {
        if (getDialogPresenter() != null) {
            getDialogPresenter().closeDialog();
        }
        MessageHelpers.cancelToasts();
    }

    protected void appendTogglePinVideoToSidebarButton() {
        appendTogglePinPlaylistButton();
        appendTogglePinChannelButton();
    }

    private void appendTogglePinPlaylistButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        Video original = getVideo();

        if (original == null || !original.hasPlaylist()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.pin_unpin_playlist),
                        optionItem -> togglePinToSidebar(createPinnedPlaylist(original))));
    }

    private void appendTogglePinChannelButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasVideo() && !original.hasReloadPageKey() && !original.hasChannel())) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.isPlaylistAsChannel() || (original.hasNestedItems() && original.belongsToUserPlaylists()) ? R.string.pin_unpin_playlist : R.string.pin_unpin_channel),
                        optionItem -> {
                            if (original.hasVideo()) {
                                MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

                                mServiceManager.loadMetadata(
                                        original,
                                        metadata -> {
                                            Video video = Video.from(original);
                                            video.sync(metadata);
                                            togglePinToSidebar(createPinnedChannel(video));
                                        }
                                );
                            } else {
                                togglePinToSidebar(createPinnedChannel(original));
                            }
                        }));
    }
    
    protected void togglePinToSidebar(Video section) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        // Toggle between pin/unpin while dialog is opened
        boolean isItemPinned = presenter.isItemPinned(section);

        if (isItemPinned) {
            presenter.unpinItem(section);
        } else {
            presenter.pinItem(section);
        }
        MessageHelpers.showMessage(getContext(), section.title + ": " + getContext().getString(isItemPinned ? R.string.unpinned_from_sidebar : R.string.pinned_to_sidebar));
    }

    private Video createPinnedPlaylist(Video video) {
        if (video == null || !video.hasPlaylist()) {
            return null;
        }

        Video section = new Video();
        section.itemType = video.itemType;
        section.playlistId = video.playlistId;
        section.playlistParams = video.playlistParams;
        // Trying to properly format channel playlists, mixes etc
        boolean isChannelPlaylistItem = video.getGroupTitle() != null && video.belongsToSameAuthorGroup() && video.belongsToSamePlaylistGroup();
        boolean isUserPlaylistItem = video.getGroupTitle() != null && video.belongsToSamePlaylistGroup();
        String title = isChannelPlaylistItem ? video.getAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isChannelPlaylistItem || isUserPlaylistItem ? video.getGroupTitle() : video.getAuthor();
        section.title = title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    private Video createPinnedChannel(Video video) {
        if (video == null || (!video.hasReloadPageKey() && !video.hasChannel() && !video.isChannel())) {
            return null;
        }

        Video section = new Video();
        section.itemType = video.itemType;
        section.channelId = video.channelId;
        section.reloadPageKey = video.getReloadPageKey();
        // Trying to properly format channel playlists, mixes etc
        boolean hasChannel = video.hasChannel() && !video.isChannel();
        boolean isUserPlaylistItem = video.getGroupTitle() != null && video.belongsToSamePlaylistGroup();
        String title = hasChannel ? video.getAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isUserPlaylistItem ? video.getGroupTitle() : hasChannel || video.isChannel() ? null : video.getAuthor();
        section.title = title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    protected void appendUnpinVideoFromSidebarButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        Video video = getVideo();

        if (video == null || (!video.hasPlaylist() && !video.hasReloadPageKey() && !video.hasChannel())) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unpin_from_sidebar),
                        optionItem -> {
                            togglePinToSidebar(video);
                            closeDialog();
                        }));
    }

    protected void appendUnpinSectionFromSidebarButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        BrowseSection section = getSection();

        if (section == null || section.getId() == MediaGroup.TYPE_SETTINGS || getVideo() != null) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unpin_from_sidebar),
                        optionItem -> {
                            BrowsePresenter.instance(getContext()).enableSection(section.getId(), false);
                            closeDialog();
                        }));
    }

    protected void appendAccountSelectionButton() {
        if (!mIsAccountSelectionEnabled) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.dialog_account_list),
                        optionItem -> AccountSelectionPresenter.instance(getContext()).show(true)
                ));
    }

    protected void appendSaveRemovePlaylistButton() {
        if (!mIsSavePlaylistEnabled) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasPlaylist() && !original.isPlaylistAsChannel() && !original.belongsToUserPlaylists())) {
            return;
        }

        // Allow removing user playlist only from Playlists section to prevent accidental deletion
        if (!BrowsePresenter.instance(getContext()).isPlaylistsSection()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.belongsToUserPlaylists()? R.string.remove_playlist : R.string.save_remove_playlist),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            if (original.hasPlaylist()) {
                                syncToggleSaveRemovePlaylist(original, null);
                            } else if (original.belongsToUserPlaylists()) {
                                // NOTE: Can't get empty playlist id. Empty playlist doesn't contain videos.
                                mServiceManager.loadChannelUploads(
                                        original,
                                        mediaGroup -> {
                                            String playlistId = getFirstPlaylistId(mediaGroup);

                                            if (playlistId != null) {
                                                syncToggleSaveRemovePlaylist(original, playlistId);
                                            } else {
                                                // Empty playlist fix. Get 'add to playlist' infos
                                                mServiceManager.getPlaylistInfos(playlistInfos -> {
                                                    List<PlaylistInfo> infos = Helpers.filter(playlistInfos, value -> Helpers.equals(
                                                            value.getTitle(), original.getTitle()));

                                                    String playlistId2 = null;

                                                    // Multiple playlists may share same name
                                                    if (infos != null && infos.size() == 1) {
                                                        playlistId2 = infos.get(0).getPlaylistId();
                                                    }

                                                    syncToggleSaveRemovePlaylist(original, playlistId2);
                                                });
                                            }
                                        }
                                );
                            } else {
                                mServiceManager.loadChannelPlaylist(
                                        original,
                                        mediaGroup -> syncToggleSaveRemovePlaylist(original, getFirstPlaylistId(mediaGroup))
                                );
                            }
                        }
                ));
    }

    private void syncToggleSaveRemovePlaylist(Video original, String playlistId) {
        Video video = Video.from(original);

        // Need correct playlist title to further comparison (decide whether save or remove)
        if (original.belongsToSamePlaylistGroup()) {
            video.title = original.group.getTitle();
        }

        if (!original.hasPlaylist() && playlistId != null) {
            video.playlistId = playlistId;
        }

        toggleSaveRemovePlaylist(video);
    }

    private void toggleSaveRemovePlaylist(Video video) {
        mServiceManager.loadPlaylists(video, group -> {
            boolean isSaved = false;

            for (MediaItem playlist : group.getMediaItems()) {
                if (playlist.getTitle().contains(video.title)) {
                    isSaved = true;
                    break;
                }
            }

            if (isSaved) {
                if (video.playlistId == null) {
                    MessageHelpers.showMessage(getContext(), R.string.cant_delete_empty_playlist);
                } else {
                    AppDialogUtil.showConfirmationDialog(getContext(), String.format("%s: %s", video.title, getContext().getString(R.string.remove_playlist)), () -> {
                        removePlaylist(video);
                        if (getCallback() != null) {
                            getCallback().onItemAction(getVideo(), VideoMenuCallback.ACTION_REMOVE);
                            closeDialog();
                        }
                    });
                }
            } else {
                savePlaylist(video);
            }
        });
    }

    private void removePlaylist(Video video) {
        MediaItemService manager = YouTubeMediaItemService.instance();
        Observable<Void> action = manager.removePlaylistObserve(video.playlistId);
        GeneralData.instance(getContext()).setPlaylistOrder(video.playlistId, -1);
        RxUtils.execute(action,
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.cant_delete_empty_playlist)),
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.removed_from_playlists))
        );
    }

    private void savePlaylist(Video video) {
        MediaItemService manager = YouTubeMediaItemService.instance();
        Observable<Void> action = manager.savePlaylistObserve(video.playlistId);
        RxUtils.execute(action,
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.cant_save_playlist)),
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.saved_to_playlists))
        );
    }

    private String getFirstPlaylistId(MediaGroup mediaGroup) {
        if (mediaGroup != null && mediaGroup.getMediaItems() != null) {
            MediaItem first = mediaGroup.getMediaItems().get(0);
            return first.getPlaylistId();
        }

        return null;
    }

    protected void appendCreatePlaylistButton() {
        if (!mIsCreatePlaylistEnabled) {
            return;
        }

        Video original = getVideo() != null ? getVideo() : new Video();

        if (original.hasVideo() || !BrowsePresenter.instance(getContext()).isPlaylistsSection()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.create_playlist),
                        optionItem -> showCreatePlaylistDialog(original)
                        ));
    }

    protected void appendAddToNewPlaylistButton() {
        if (!mIsAddToNewPlaylistEnabled) {
            return;
        }

        Video original = getVideo() != null ? getVideo() : new Video();

        if (!original.hasVideo()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.add_video_to_new_playlist),
                        optionItem -> showCreatePlaylistDialog(original)
                ));
    }

    private void showCreatePlaylistDialog(Video video) {
        closeDialog();
        SimpleEditDialog.show(
                getContext(),
                "",
                newValue -> {
                    MediaItemService manager = YouTubeMediaItemService.instance();
                    Observable<Void> action = manager.createPlaylistObserve(newValue, video.hasVideo() ? video.videoId : null);
                    RxUtils.execute(
                            action,
                            () -> MessageHelpers.showMessage(getContext(), newValue + ": " + getContext().getString(R.string.cant_save_playlist)),
                            () -> {
                                if (!video.hasVideo()) { // Playlists section
                                    BrowsePresenter.instance(getContext()).refresh();
                                } else {
                                    MessageHelpers.showMessage(getContext(), newValue + ": " + getContext().getString(R.string.saved_to_playlists));
                                }
                            }
                    );
                },
                getContext().getString(R.string.create_playlist),
                true
        );
    }

    protected void appendRenamePlaylistButton() {
        if (!mIsCreatePlaylistEnabled) {
            return;
        }

        Video original = getVideo();

        if (original == null || !BrowsePresenter.instance(getContext()).isPlaylistsSection()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.rename_playlist),
                        optionItem -> showRenamePlaylistDialog(original)
                ));
    }

    private void showRenamePlaylistDialog(Video video) {
        MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
        mServiceManager.loadChannelUploads(
                video,
                mediaGroup -> {
                    closeDialog();

                    if (mediaGroup == null) { // crash fix
                        return;
                    }

                    MediaItem firstItem = mediaGroup.getMediaItems().get(0);

                    if (firstItem.getPlaylistId() == null) {
                        MessageHelpers.showMessage(getContext(), R.string.cant_rename_empty_playlist);
                        return;
                    }

                    SimpleEditDialog.show(
                            getContext(),
                            video.title,
                            newValue -> {
                                MediaItemService manager = YouTubeMediaItemService.instance();
                                Observable<Void> action = manager.renamePlaylistObserve(firstItem.getPlaylistId(), newValue);
                                RxUtils.execute(
                                        action,
                                        () -> MessageHelpers.showMessage(getContext(), R.string.owned_playlist_warning),
                                        () -> {
                                            video.title = newValue;
                                            BrowsePresenter.instance(getContext()).syncItem(video);
                                        }
                                );
                            },
                            getContext().getString(R.string.rename_playlist),
                            true
                    );
                }
        );
    }

    protected void appendToggleHistoryButton() {
        if (!mIsToggleHistoryEnabled) {
            return;
        }

        if (getSection() != null && getSection().getId() != MediaGroup.TYPE_HISTORY) {
            return;
        }

        GeneralData generalData = GeneralData.instance(getContext());
        boolean enabled = generalData.isHistoryEnabled();

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(enabled ? R.string.pause_history : R.string.resume_history),
                        optionItem -> {
                            mServiceManager.enableHistory(!enabled);
                            generalData.enableHistory(!enabled);
                            getDialogPresenter().closeDialog();
                        }));
    }

    protected void appendClearHistoryButton() {
        if (!mIsClearHistoryEnabled) {
            return;
        }

        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (!presenter.isHistorySection()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.clear_history),
                        optionItem -> {
                            mServiceManager.clearHistory();
                            getDialogPresenter().closeDialog();
                            presenter.refresh();
                        }));
    }

    protected void appendUpdateCheckButton() {
        if (!mIsUpdateCheckEnabled) {
            return;
        }

        getDialogPresenter().appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true)));
    }

    protected void updateEnabledMenuItems() {
        MainUIData mainUIData = MainUIData.instance(getContext());
        
        mIsPinToSidebarEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);
        mIsSavePlaylistEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SAVE_PLAYLIST);
        mIsCreatePlaylistEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_CREATE_PLAYLIST);
        mIsAccountSelectionEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SELECT_ACCOUNT);
        mIsAddToNewPlaylistEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST);
        mIsToggleHistoryEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_TOGGLE_HISTORY);
        mIsClearHistoryEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_CLEAR_HISTORY);
        mIsUpdateCheckEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_UPDATE_CHECK);
    }
}
