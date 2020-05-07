package education.mahmoud.quranyapp.feature.listening_activity

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.fragment.app.Fragment
import butterknife.ButterKnife
import butterknife.OnClick
import butterknife.Unbinder
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import education.mahmoud.quranyapp.R
import education.mahmoud.quranyapp.datalayer.Repository
import education.mahmoud.quranyapp.datalayer.local.room.AyahItem
import education.mahmoud.quranyapp.datalayer.local.room.SuraItem
import education.mahmoud.quranyapp.feature.home_Activity.HomeActivity
import education.mahmoud.quranyapp.utils.Util
import kotlinx.android.synthetic.main.fragment_listen.*
import org.koin.java.KoinJavaComponent
import java.io.IOException
import java.text.MessageFormat
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class ListenFragment : Fragment(), OnDownloadListener {
    var mediaPlayer: MediaPlayer? = null
    var url = "http://cdn.alquran.cloud/media/audio/ayah/ar.alafasy/"
    var isPermissionAllowed = false
    var downloadID = 0
    var i = 1
    var startSura: SuraItem? = null
    var endSura: SuraItem? = null
    var downURL: String? = null
    var path: String? = null
    var filename: String? = null
    var index = 0
    var currentAyaAtAyasToListen = 0
    var fileSource: String? = null
    var ayahsToListen = listOf<AyahItem>()
    var actualStart = 0
    var actualEnd = 0
    var currentIteration = 0
    var endIteration = 0
    private var unbinder: Unbinder? = null
    private val repository = KoinJavaComponent.get(Repository::class.java)
    private var ayahsToDownLoad = listOf<AyahItem>()
    private var ayahsRepeatCount = 0
    private var ayahsSetCount = 0
    var handler: Handler? = null
    var serviceIntent: Intent? = null
    private fun initSpinners() {
        val suraNames = repository.surasNames
        val startAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suraNames)
        spStartSura.adapter = startAdapter
        val endAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suraNames)
        spEndSura.adapter = endAdapter
        spStartSura.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, index: Long) {
                try {
                    startSura = repository.getSuraByIndex(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        spEndSura.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, index: Long) {
                try {
                    endSura = repository.getSuraByIndex(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
    }

    //<editor-fold desc="downolad">
    private fun downloadAudio() { // compute index
        index = ayahsToDownLoad[currentIteration].ayahIndex
        // form  URL
        downURL = url + index
        // form path
        path = Util.getDirectoryPath() // get folder path
        // form file name
        filename = "$index.mp3"
        Log.d(TAG, "downloadAudio:  file name $filename")
        //start downloading
        PRDownloader.download(downURL, path, filename).build().start(this)
        // set text on screen downloaded / todownled
// second is show name of current file to download
        tvDownCurrentFile.setText(getString(R.string.now_down, filename))
        tvDownStatePercentage.setText(getString(R.string.downState, currentIteration, endIteration))
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        Log.d(TAG, "setUserVisibleHint: ")
        if (isVisibleToUser && lnPlayView != null) {
            initSpinners()
            backToSelectionState()
        }
    }

    //<editor-fold desc="download ">
    override fun onDownloadComplete() {
        Log.d(TAG, "onDownloadComplete: ")
        // store storage path in db to use in media player
        val ayahItem = repository.getAyahByIndex(index) // first get ayah to edit it with storage path
        val storagePath = "$path/$filename"
        ayahItem?.audioPath = storagePath // set path
        repository.updateAyahItem(ayahItem)
        // update currentIteration to indicate complete of download
        currentIteration++
        Log.d(TAG, "onDownloadComplete:  end $endIteration")
        Log.d(TAG, "onDownloadComplete:  current $currentIteration")
        if (currentIteration < endIteration) { // still files to download
            downloadAudio()
        } else { // here i finish download all ayas
// start to display
            finishDownloadState()
            displayAyasState()
        }
    }

    override fun onError(error: Error) {
        if (error.isConnectionError) {
            showMessage(getString(R.string.error_net))
        } else if (error.isServerError) {
            showMessage("Server error")
        } else {
            showMessage("Error $error")
        }
        lnDownState.setVisibility(View.GONE)
        backToSelectionState()
    }

    //</editor-fold>
    private fun finishDownloadState() {
        showMessage(getString(R.string.finish))
        btnStartListening.setVisibility(View.VISIBLE)
        lnDownState.setVisibility(View.GONE)
    }

    //</editor-fold>
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_listen, container, false)
        unbinder = ButterKnife.bind(this, view)
        isPermissionAllowed = repository.permissionState
        serviceIntent = Intent(context, ListenServie::class.java)
        initSpinners()
        handler = object : Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (mediaPlayer != null && isVisible) {
                    tvProgressAudio.setText(getString(R.string.time_progress, mediaPlayer.currentPosition / 1000
                            , mediaPlayer.duration / 1000))
                    sbPosition.setProgress(mediaPlayer.currentPosition)
                }
            }
        }
        return view
    }

    private fun displayAyasState() {
        Log.d(TAG, "display Ayas State: ")
        currentAyaAtAyasToListen = 0
        // first reload ayahs from db
        ayahsToListen = repository.getAyahSInRange(actualStart + 1, actualEnd + 1)
        // repeation formation
        ayahsToListen = getAyahsEachOneRepreated(ayahsRepeatCount)
        ayahsToListen = getAllAyahsRepeated(ayahsSetCount)
        // control visibility
        lnSelectorAyahs.setVisibility(View.GONE)
        lnPlayView.setVisibility(View.VISIBLE)
        btnPlayPause.setBackgroundResource(R.drawable.ic_pause)
        // // TODO: 6/30/2019  bind service with this.
        displayAyahs()
        //<editor-fold desc="start audio service">
        val ayahsListen = AyahsListen()
        ayahsListen.setAyahItemList(ayahsToListen)
        if (serviceIntent != null) {
            activity.stopService(serviceIntent)
        }
        serviceIntent = ListenServie.createService(context, ayahsListen)
        //</editor-fold>
    }

    private fun getAllAyahsRepeated(ayahsSetCount: Int): List<AyahItem> {
        val ayahItems = mutableListOf<AyahItem>()
        for (i in 0 until ayahsSetCount) {
            ayahItems.addAll(ayahsToListen)
        }
        Log.d(TAG, "getAllAyahsRepeated: " + ayahItems.size)
        return ayahItems
    }

    private fun getAyahsEachOneRepreated(ayahsRepeatCount: Int): List<AyahItem> {
        val ayahItems: MutableList<AyahItem> = ArrayList()
        for (ayahItem in ayahsToListen) {
            for (j in 0 until ayahsRepeatCount) {
                ayahItems.add(ayahItem)
            }
        }
        return ayahItems
    }

    private fun logAyahs() {
        for (ayahItem in ayahsToListen) {
            Log.d(TAG, "logAyahs: " + ayahItem.ayahIndex + ayahItem.text)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun displayAyahs() {
        Log.d(TAG, "displayAyahs: $currentAyaAtAyasToListen")
        val ayahItem = ayahsToListen[currentAyaAtAyasToListen]
        tvAyahToListen.setText(MessageFormat.format("{0} ﴿ {1} ﴾ ", ayahItem.text, ayahItem.ayahInSurahIndex))
        // showMessage("size " + ayahsToListen.size());
        playAudio()
    }

    @OnClick(R.id.btnPlayPause)
    fun onBtnPlayPauseClicked() {
        Log.d(TAG, "onBtnPlayPauseClicked: ")
        if (mediaPlayer != null) {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
                Log.d(TAG, "onBtnPlayPauseClicked: ")
                //     btnPlayPause.setBackground(getDrawable(R.drawable.ic_pause));
                btnPlayPause.setBackgroundResource(R.drawable.ic_pause)
            } else { //    btnPlayPause.setBackground(getDrawable(R.drawable.ic_play));
                btnPlayPause.setBackgroundResource(R.drawable.ic_play)
                mediaPlayer.pause()
            }
        }
    }

    @OnClick(R.id.btnStartListening)
    fun onViewClicked() {
        ayahsToDownLoad = ArrayList()
        ayahsToListen = ArrayList()
        //region check inputs
        if (startSura != null && endSura != null) {
            try {
                val start: Int = edStartSuraAyah.getText().toString().toInt()
                if (start > startSura.numOfAyahs) {
                    edStartSuraAyah.setError(getString(R.string.outofrange, startSura.numOfAyahs))
                    return
                }
                val end: Int = edEndSuraAyah.getText().toString().toInt()
                if (end > endSura.numOfAyahs) {
                    edEndSuraAyah.setError(getString(R.string.outofrange, endSura.numOfAyahs))
                    return
                }
                // compute actual start
                actualStart = repository.getAyahByInSurahIndex(startSura.index, start).ayahIndex - 1
                // compute actual end
                actualEnd = repository.getAyahByInSurahIndex(endSura.index, end).ayahIndex - 1
                // check actualstart & actualEnd
                if (actualEnd < actualStart) {
                    makeRangeError()
                    return
                }
                Log.d(TAG, "onViewClicked: actual $actualStart $actualEnd")
                ayahsSetCount = try {
                    edRepeatSet.getText().toString().toInt()
                } catch (e: NumberFormatException) {
                    1
                }
                ayahsRepeatCount = try {
                    edRepeatAyah.getText().toString().toInt()
                } catch (e: NumberFormatException) {
                    1
                }
                // get ayahs from db,
// actual end is updated with one as query return result excluded one item
                ayahsToListen = repository.getAyahSInRange(actualStart + 1, actualEnd + 1)
                Log.d(TAG, "onViewClicked: start log after first select " + ayahsToListen.size)
                logAyahs()
                // traverse ayahs to check if it downloaded or not
                for (ayahItem in ayahsToListen) {
                    if (ayahItem.audioPath == null) {
                        ayahsToDownLoad.add(ayahItem)
                    }
                }
                // close keyboard
                closeKeyboard()
                checkAyahsToDownloadIt()
            } catch (e: NumberFormatException) {
                makeRangeError()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            showMessage(getString(R.string.sura_select_error))
        }
        //endregion
    }

    private fun playAudio() {
        Log.d(TAG, "playAudio:  current $currentAyaAtAyasToListen")
        btnPlayPause.setEnabled(false)
        try {
            mediaPlayer = MediaPlayer()
            fileSource = ayahsToListen[currentAyaAtAyasToListen].audioPath
            mediaPlayer.setDataSource(fileSource)
            Log.d(TAG, "playAudio: file source $fileSource")
            mediaPlayer.prepare()
            mediaPlayer.setOnPreparedListener { mediaPlayer -> mediaPlayer.start() }
            sbPosition.setMax(mediaPlayer.duration)
            Thread(Runnable {
                while (mediaPlayer != null) {
                    try {
                        handler.sendEmptyMessage(0)
                        Thread.sleep(750)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }).start()
            sbPosition.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    if (b) {
                        if (mediaPlayer != null) {
                            mediaPlayer.seekTo(progress)
                            sbPosition.setProgress(progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
            mediaPlayer.setOnCompletionListener {
                mediaPlayer.release()
                mediaPlayer = null
                btnPlayPause.setEnabled(true)
                currentAyaAtAyasToListen++
                if (currentAyaAtAyasToListen < ayahsToListen.size) {
                    Log.d(TAG, "@@  onCompletion: $currentAyaAtAyasToListen")
                    displayAyahs()
                } else {
                    actualStart = -1
                    actualEnd = -1
                    backToSelectionState()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            showMessage(getString(R.string.error))
            backToSelectionState()
        }
    }

    private fun closeKeyboard() {
        edEndSuraAyah.clearFocus()
        edStartSuraAyah.clearFocus()
        lnPlayView.requestFocus()
        //    Util.hideInputKeyboard(getContext());
    }

    private fun makeRangeError() {
        edStartSuraAyah.setError("Start must be before end ")
        edEndSuraAyah.setError("End must be after start")
    }

    private fun checkAyahsToDownloadIt() {
        Log.d(TAG, "checkAyahsToDownloadIt: " + ayahsToDownLoad.size)
        currentIteration = 0
        if (ayahsToDownLoad != null && ayahsToDownLoad.size > 0) {
            endIteration = ayahsToDownLoad.size
            downloadAyahs()
        } else {
            displayAyasState()
        }
    }

    private fun downloadAyahs() {
        Log.d(TAG, "downloadAyahs: ")
        if (!isPermissionAllowed) {
            (activity as HomeActivity?).acquirePermission()
        }
        downloadState()
        downloadAudio()
    }

    private fun downloadState() {
        showMessage(getString(R.string.downloading))
        btnStartListening.setVisibility(View.GONE)
        lnDownState.setVisibility(View.VISIBLE)
    }

    private fun backToSelectionState() {
        if (mediaPlayer != null) {
            mediaPlayer.release()
            mediaPlayer = null
        }
        // control visibility
        lnPlayView.setVisibility(View.GONE)
        lnSelectorAyahs.setVisibility(View.VISIBLE)
        btnStartListening.setVisibility(View.VISIBLE)
        lnDownState.setVisibility(View.GONE)
        // clear inputs
        edEndSuraAyah.setText(null)
        edStartSuraAyah.setText(null)
        edEndSuraAyah.setError(null)
        edStartSuraAyah.setError(null)
        edRepeatAyah.setText(null)
        edRepeatSet.setText(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbinder.unbind()
    }

    companion object {
        private const val TAG = "ListenFragment"
    }
}