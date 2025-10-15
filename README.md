#Android Phishing URL Detection

This is an Android application for phishing URL detection. The model is trained using deep learning (CRNN) and the dataset used is available at: https://github.com/ebubekirbbr/dephides

Description

A simple Android app that classifies URLs as phishing or benign using a CRNN (Convolutional Recurrent Neural Network) trained on the Dephides dataset. The app demonstrates how a lightweight deep-learning model can be integrated into an Android application for real-time URL screening.


Dataset

The dataset used for training is the Dephides dataset. Repository: https://github.com/ebubekirbbr/dephides

Model

Architecture: CRNN (Convolutional + Recurrent layers)

Task: Binary classification (phishing / benign)

Input: URL text or extracted URL features

Output: Probability or label indicating phishing or not

Files in this repo

app/ — Android application source (example integration)

model/ — Trained model files (TensorFlow Lite / ONNX / other format)

train/ — Training scripts and notebooks (CRNN implementation)

data/ — (Optional) data processing scripts and pointers to dataset

README.md — Project description (this file)

Installation (Developer)

the file of apk can be found here : https://www.kaggle.com/models/manishhr/phishing_url_detection_using_android/

Usage

Launch the app on an Android device.

Enter or paste a URL in the input box.

The app returns a prediction: Phishing or Benign (with confidence score).

Training (High-level)

Preprocess URLs from the Dephides dataset (tokenization/encoding).

Train the CRNN model using the training scripts.

Convert the trained model to a mobile-friendly format.

Place the converted model in android app/ and integrate into app/.


If you use this work in research or projects, please acknowledge the dataset source and the model approach.

Contact

For questions or collaboration, open an issue or contact the repository owner.
