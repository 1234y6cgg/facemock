from io import BytesIO
import unittest
from PIL import Image
from engine import decode_image, OcrEngine
class EngineTests(unittest.TestCase):
    def image_bytes(self,fmt="PNG"):
        out=BytesIO()
        with Image.new("RGB",(80,120),"white") as image: image.save(out,format=fmt)
        return out.getvalue()
    def test_supported_images(self):
        for fmt in ("PNG","JPEG","WEBP"):
            with decode_image(self.image_bytes(fmt)) as image: self.assertEqual(image.size,(80,120))
    def test_invalid_and_unsupported_images(self):
        for data in (b"",b"not an image",self.image_bytes("BMP")):
            with self.assertRaises(ValueError): decode_image(data)
    def test_blank_and_low_confidence_text_is_excluded(self):
        class Result: json={"res":{"rec_texts":["Java Engineer","noise"," "],"rec_scores":[0.99,0.2,0.99]}}
        class Pipeline:
            def predict(self,image,**kwargs):return [Result()]
        with decode_image(self.image_bytes()) as image:
            self.assertEqual(OcrEngine(Pipeline()).extract(image)["text"],"Java Engineer")
if __name__=="__main__":unittest.main()
