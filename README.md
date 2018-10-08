 # SSD_Demo_Android
基于 mxnet, 实现 ssd demo for android

感谢来自 [botyourbusiness](https://github.com/botyourbusiness/android-camera2-secret-picture-taker) , 从中引用了代码用于实现周期获取摄像头图片.

## 准备
需要编译 openblas, mxnet amalgamation, 参见 [https://github.com/apache/incubator-mxnet/tree/master/amalgamation]

需要注意的是, ssd 依赖更多的 operator, 在 mxnet_predict0.cc 中加上即可.

另外生成的 mxnet_predict-all.cc 中有一个 #include <x86intrin.h> 需要删除, 修改为 #include <endian.h>, 当然也可以将 x86intrin.h 加到 amalgamation.py 的 blacklist 中, 并将 endian.h 加入到 sysheaders 变量中.

我测试了 ssd_mobilenet1.0_voc, yolo3_darknet53_voc, 发现至少需要在 mxnet_predict0.cc 中增加以下 .cc 文件:

    #include "src/operator/slice_channel.cc"
    #include "src/operator/nn/softmax.cc"
    #include "src/operator/tensor/elemwise_binary_scalar_op_logic.cc"
    #include "src/operator/tensor/control_flow_op.cc"
    #include "src/operator/contrib/multibox_detection.cc"
    #include "src/operator/contrib/multibox_prior.cc"
    #include "src/operator/contrib/bounding_box.cc"
    #include "src/storage/storage.cc"


## 通过 GluonCV model_zoo 下载模型
[gluoncv](https://gluon-cv.mxnet.io/model_zoo/index.html) 提供了大量可直接使用的模型, 可以简单的导出为 libmxnet_predict.so 使用的 .json + .params 格式.

    from gluoncv import model_zoo, data
    import mxnet as mx
    import cv2
    
    model_zoo.get_model_list()
    net = model_zoo.get_model("ssd_512_mobilenet1_0_voc", pretrained=True)
    net.hybridize()

    data, img = data.transforms.presets.ssd.load_test("P9297176.JPG", short=512)
    r = net(data)
    net.export("ssd_mobile_voc")    # 生成 ssd_mobile_voc-symbol.json 和 ssd_mobile_voc-0000.params

    classnames = net.classes
    catalogs, scores, boxes = r

    for i, score in scores:
        if score < 0.4:
            continue

        catalog = int(catalogs[i])
        x1, y1 = int(boxes[4*i]), int(boxes[4*i+1])
        x2, y2 = int(boxes[4*i+2]), int(boxes[4*i+3])
        
        cv2.rectangle(img, (x1,y1), (x2,y2), (0,0,255))
        cv2.putText(img, classnames[catalog], (x1,y1), cv2.FONT_HERSHEY_COMPLEX_SMALL, 1.0, (0,255,255))
    
    cv2.imshow("debug", img)
    cv2.waitKey(0)

将生成的 .json 和 .params 放到 assets 目录中