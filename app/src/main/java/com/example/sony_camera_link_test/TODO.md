TODO

FOUNDATION
[x] Load image into Bitmap
[x] Extract RGB pixel values
[x] Create RGB vectors
[x] Implement Euclidean distance function
[x] Redesign UI

K-MEANS
[x] Randomly initialize centroids
[x] Assign pixels to nearest centroid
[x] Group pixels into clusters
[x] Average cluster members
[x] Move centroids
[X] Repeat until convergence
[X] Create reusable KMeans class

VISUALIZATION
[] Visualize dominant colors
[] Display cluster centroids
[] Reconstruct clustered image
[] Add before/after image comparison

IMAGE PROCESSING
[x] Move grayscale filter into ImageProcessor
[x] Add image resizing helper
[x] Make an image resizing helper that applies computed mask to original image
[] Add bitmap copy utilities?
[] Add RGB normalization helper

SONY CAMERA
[x] Connect to Sony camera API
[x] Create SonyCameraClient
[x] Download captured image as Bitmap
[] Handle camera disconnects
[x] Add loading/error states
[x] Save captured images locally

ANDROID ARCHITECTURE
[x] Move networking out of MainActivity
[x] Move image processing out of MainActivity
[x] Create ImageProcessor class
[] Move any math functions to MathUtils
[] Create RGBPixel model?
[] Create Cluster model?
[x] Reduce MainActivity to UI-only logic

UI REFORMATING
[x] Standardize the colour scheme
[x] Apply colour scheme to XML/Text
[x] Make side menus
[X] Populate and test side menus
[] Fix the fall back system camera option
[] move sony cam to camera menu
[] Find solution for zoom bar
[] Reformat the main image, camera, filter view

PERFORMANCE
[] try to get around getPixel() bottleneck?
[x] Move image processing off UI thread
[x] Add downsampling for large images

FUTURE
[x] Add pixelizer to images
[] Try to create pixel art effect with conv net