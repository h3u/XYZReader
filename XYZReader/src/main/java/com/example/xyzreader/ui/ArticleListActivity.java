package com.example.xyzreader.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private boolean mUseGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setTitle("");

        // for tablet devices use the grid layout
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            mUseGrid = true;
        }

        // add listener to call refresh when user swipes down
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.theme_accent, R.color.theme_primary, R.color.theme_primary_dark);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        getLoaderManager().initLoader(0, null, this);

        RecyclerView.LayoutManager lm;
        if (!mUseGrid) {
            lm = new LinearLayoutManager(ArticleListActivity.this,LinearLayoutManager.VERTICAL,false);
            mRecyclerView.addItemDecoration(new DividerItemDecoration(
                    this, ContextCompat.getDrawable(this, R.drawable.padded_divider),
                    DividerItemDecoration.VERTICAL_LIST, false));
            mRecyclerView.setLayoutManager(lm);
        }

        if (savedInstanceState == null) {
            refresh();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        adapter.setIsGrid(mUseGrid);
        mRecyclerView.setAdapter(adapter);

        if (mUseGrid) {
            int columnCount = getResources().getInteger(R.integer.list_column_count);
            StaggeredGridLayoutManager sglm =
                    new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(sglm);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ItemViewHolder> {
        private Cursor mCursor;
        private boolean mIsGrid;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        public void setIsGrid(boolean isGrid) {
            mIsGrid = isGrid;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layout = mIsGrid ? R.layout.grid_item_article : R.layout.list_item_article;
            View view = getLayoutInflater().inflate(layout, parent, false);
            final ItemViewHolder vh;
            if (mIsGrid) {
                vh = new GridItemViewHolder(view);
            } else {
                vh = new ItemViewHolder(view);
            }
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition()))));
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final ItemViewHolder holder, int position) {
            mCursor.moveToPosition(position);

            Float ratio = mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO);
            String title = mCursor.getString(ArticleLoader.Query.TITLE);

            holder.titleView.setText(title);
            holder.subtitleView.setText(String.format(getString(R.string.list_item_subtitle),
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString(),
                            mCursor.getString(ArticleLoader.Query.AUTHOR)));
            if (holder instanceof GridItemViewHolder) {
                ((GridItemViewHolder)holder).thumbnailView.setImageUrl(
                        mCursor.getString(ArticleLoader.Query.THUMB_URL),
                        ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
                ((GridItemViewHolder)holder).titleInverseView.setText(title);
                ((GridItemViewHolder)holder).thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
            } else {
                // set accent color for progress bar
                if (holder.thumbnailProgressBar.getIndeterminateDrawable() != null) {
                    holder.thumbnailProgressBar.getIndeterminateDrawable()
                            .setColorFilter(getResources().getColor(R.color.theme_accent), android.graphics.PorterDuff.Mode.SRC_IN);
                }
                Picasso.with(ArticleListActivity.this).load(mCursor.getString(ArticleLoader.Query.THUMB_URL))
                        .error(R.drawable.image_error)
                        .transform(new RoundedTransformation(50, 0))
                        .resizeDimen(R.dimen.list_item_image_size, R.dimen.list_item_image_size)
                        .into(holder.thumbnailView, new Callback() {
                            @Override
                            public void onSuccess() {
                                holder.thumbnailProgressBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError() {
                                holder.thumbnailProgressBar.setVisibility(View.GONE);
                            }
                        });
            }

            // switch layout for ration <= 1.0f
            if (mIsGrid) {
                if (ratio <= 1.0f) {
                    // change visibility of title views
                    holder.titleView.setVisibility(View.GONE);
                    if (holder instanceof GridItemViewHolder) {
                        ((GridItemViewHolder) holder).titleInverseView.setVisibility(View.VISIBLE);
                    }
                } else {
                    holder.titleView.setVisibility(View.VISIBLE);
                    if (holder instanceof GridItemViewHolder) {
                        ((GridItemViewHolder) holder).titleInverseView.setVisibility(View.GONE);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnailView;
        public ProgressBar thumbnailProgressBar;
        public TextView titleView;
        public TextView subtitleView;

        public ItemViewHolder(View view) {
            super(view);
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            thumbnailProgressBar = (ProgressBar) view.findViewById(R.id.thumbnail_progressbar);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }

    public static class GridItemViewHolder extends ItemViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleInverseView;

        public GridItemViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleInverseView = (TextView) view.findViewById(R.id.article_title_inverse);
        }
    }
}
