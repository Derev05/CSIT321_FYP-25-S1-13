    package com.example.kotlinbasics

    import android.content.Context
    import org.tensorflow.lite.Interpreter
    import java.nio.MappedByteBuffer
    import java.nio.channels.FileChannel
    import java.io.FileInputStream
    import java.nio.ByteBuffer

    class TFLiteHelper(context: Context) {
        private var interpreter: Interpreter? = null

        init {
            interpreter = Interpreter(loadModelFile(context, "model.tf-lite"))
        }

        private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
            val fileDescriptor = context.assets.openFd(modelFileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
        }

        fun runInference(input: FloatArray): FloatArray {
            val output = Array(1) { FloatArray(1) }  // Change based on model output
            interpreter?.run(input, output)
            return output[0]
        }

        fun close() {
            interpreter?.close()
        }
    }
